package com.agentdsl.langchain4j;

import com.agentdsl.core.spec.MemorySpec;
import com.agentdsl.core.spec.ModelSpec;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * LangChain4j 记忆工厂。
 * 先处理内建 memory，再从类路径动态发现插件实现。
 *
 * 插件约定：
 * 1. 在 `META-INF/agentdsl/memory.providers` 中声明 provider 类名
 * 2. provider 需要提供 `String getType()` 方法
 * 3. provider 需要提供 `ChatMemory create(Map<String, Object> config)` 方法
 *
 * 这里使用反射而不是编译期接口，目的是让插件模块可以独立发布，
 * 不必依赖 AgentDSL 内部模块，只需依赖 LangChain4j 即可。
 */
public class LangChainMemoryFactory {

    private static final Logger log = LoggerFactory.getLogger(LangChainMemoryFactory.class);
    private static final String PROVIDER_RESOURCE = "META-INF/agentdsl/memory.providers";
    public static final String INTERNAL_COMPRESSION_CHAT_MODEL = "__agentdsl.memory.compressionChatModel";
    public static final String INTERNAL_RECONSTRUCTION_CHAT_MODEL = "__agentdsl.memory.reconstructionChatModel";
    public static final String INTERNAL_EMBEDDING_MODEL = "__agentdsl.memory.embeddingModel";

    private volatile Map<String, Object> discoveredProviders;
    private volatile LangChainModelFactory modelFactory;
    private volatile LangChainEmbeddingFactory embeddingFactory;

    public LangChainMemoryFactory() {
        this(new LangChainModelFactory(), new LangChainEmbeddingFactory());
    }

    public LangChainMemoryFactory(LangChainModelFactory modelFactory, LangChainEmbeddingFactory embeddingFactory) {
        this.modelFactory = modelFactory;
        this.embeddingFactory = embeddingFactory;
    }

    public void bindModelFactory(LangChainModelFactory modelFactory) {
        if (modelFactory != null) {
            this.modelFactory = modelFactory;
        }
    }

    public void bindEmbeddingFactory(LangChainEmbeddingFactory embeddingFactory) {
        if (embeddingFactory != null) {
            this.embeddingFactory = embeddingFactory;
        }
    }

    /**
     * 根据 MemorySpec 创建 ChatMemory。
     * 如果 spec 为 null，返回默认的 10 条消息窗口记忆。
     */
    public ChatMemory create(MemorySpec spec) {
        return create(spec, null, null);
    }

    public ChatMemory create(MemorySpec spec, ModelSpec agentModelSpec, ChatModel agentChatModel) {
        if (spec == null) {
            log.debug("未配置记忆，使用默认 MessageWindowChatMemory(10)");
            return MessageWindowChatMemory.withMaxMessages(10);
        }

        String type = spec.getType() != null ? spec.getType().trim() : "message_window";
        log.info("创建记忆: type={}", type);
        Map<String, Object> config = buildConfig(spec, agentModelSpec, agentChatModel);

        return switch (type) {
            case "message_window" -> MessageWindowChatMemory.withMaxMessages(
                    spec.getMaxMessages() != null ? spec.getMaxMessages() : 20);
            case "token_window" -> {
                int maxTokens = spec.getMaxTokens() != null ? spec.getMaxTokens() : 4000;
                yield TokenWindowChatMemory.withMaxTokens(maxTokens,
                        new OpenAiTokenCountEstimator("gpt-4"));
            }
            default -> {
                ChatMemory pluginMemory = createFromPlugin(type, config);
                if (pluginMemory != null) {
                    yield pluginMemory;
                }
                log.warn("未知的记忆类型: {}，且未发现可用插件，回退到 message_window", type);
                yield MessageWindowChatMemory.withMaxMessages(20);
            }
        };
    }

    private Map<String, Object> buildConfig(MemorySpec spec, ModelSpec agentModelSpec, ChatModel agentChatModel) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", spec.getType());
        if (spec.getMaxMessages() != null) {
            config.put("maxMessages", spec.getMaxMessages());
        }
        if (spec.getMaxTokens() != null) {
            config.put("maxTokens", spec.getMaxTokens());
        }
        if (spec.getStm() != null) {
            Map<String, Object> stm = new LinkedHashMap<>();
            if (spec.getStm().getMaxEdges() != null) {
                stm.put("maxEdges", spec.getStm().getMaxEdges());
            }
            if (spec.getStm().getTtlHours() != null) {
                stm.put("ttlHours", spec.getStm().getTtlHours());
            }
            if (!stm.isEmpty()) {
                config.put("stm", stm);
            }
        }
        if (spec.getLtm() != null) {
            Map<String, Object> ltm = new LinkedHashMap<>();
            putIfNotNull(ltm, "backend", spec.getLtm().getBackend());
            putIfNotNull(ltm, "path", spec.getLtm().getPath());
            putIfNotNull(ltm, "compressionModel", spec.getLtm().getCompressionModel());
            if (!ltm.isEmpty()) {
                config.put("ltm", ltm);
            }
        }
        if (spec.getVector() != null) {
            Map<String, Object> vector = new LinkedHashMap<>();
            putIfNotNull(vector, "store", spec.getVector().getStore());
            putIfNotNull(vector, "embeddingModel", spec.getVector().getEmbeddingModel());
            putIfNotNull(vector, "path", spec.getVector().getPath());
            if (!vector.isEmpty()) {
                config.put("vector", vector);
            }
        }
        if (spec.getDecay() != null) {
            Map<String, Object> decay = new LinkedHashMap<>();
            putIfNotNull(decay, "baseRate", spec.getDecay().getBaseRate());
            putIfNotNull(decay, "importanceBoost", spec.getDecay().getImportanceBoost());
            putIfNotNull(decay, "compressionThreshold", spec.getDecay().getCompressionThreshold());
            putIfNotNull(decay, "archiveThreshold", spec.getDecay().getArchiveThreshold());
            if (!decay.isEmpty()) {
                config.put("decay", decay);
            }
        }
        if (spec.getConsolidation() != null) {
            Map<String, Object> consolidation = new LinkedHashMap<>();
            putIfNotNull(consolidation, "intervalHours", spec.getConsolidation().getIntervalHours());
            if (!consolidation.isEmpty()) {
                config.put("consolidation", consolidation);
            }
        }
        if (spec.getDeepRecallThreshold() != null) {
            config.put("deepRecallThreshold", spec.getDeepRecallThreshold());
        }
        config.putAll(spec.getOptions());
        attachRuntimeModels(config, spec, agentModelSpec, agentChatModel);
        return Collections.unmodifiableMap(config);
    }

    private void attachRuntimeModels(Map<String, Object> config,
            MemorySpec memorySpec,
            ModelSpec agentModelSpec,
            ChatModel agentChatModel) {
        if (agentChatModel != null) {
            config.put(INTERNAL_RECONSTRUCTION_CHAT_MODEL, agentChatModel);
        }

        if (memorySpec.getLtm() != null
                && memorySpec.getLtm().getCompressionModel() != null
                && !memorySpec.getLtm().getCompressionModel().isBlank()
                && agentModelSpec != null) {
            ChatModel compressionChatModel = createDerivedChatModel(
                    agentModelSpec,
                    agentChatModel,
                    memorySpec.getLtm().getCompressionModel());
            config.put(INTERNAL_COMPRESSION_CHAT_MODEL, compressionChatModel);
            config.put(INTERNAL_RECONSTRUCTION_CHAT_MODEL, compressionChatModel);
        }

        if (memorySpec.getVector() != null
                && memorySpec.getVector().getEmbeddingModel() != null
                && !memorySpec.getVector().getEmbeddingModel().isBlank()
                && agentModelSpec != null) {
            EmbeddingModel embeddingModel = embeddingFactory.create(
                    agentModelSpec,
                    memorySpec.getVector().getEmbeddingModel());
            config.put(INTERNAL_EMBEDDING_MODEL, embeddingModel);
        }
    }

    private ChatModel createDerivedChatModel(ModelSpec baseSpec, ChatModel agentChatModel, String modelName) {
        if (agentChatModel != null && Objects.equals(baseSpec.getModelName(), modelName)) {
            return agentChatModel;
        }
        ModelSpec derived = copyModelSpec(baseSpec);
        derived.setModelName(modelName);
        return modelFactory.create(derived);
    }

    private ModelSpec copyModelSpec(ModelSpec source) {
        ModelSpec target = new ModelSpec();
        target.setProvider(source.getProvider());
        target.setModelName(source.getModelName());
        target.setApiKey(source.getApiKey());
        target.setBaseUrl(source.getBaseUrl());
        target.setTemperature(source.getTemperature());
        target.setTopP(source.getTopP());
        target.setMaxTokens(source.getMaxTokens());
        target.setTimeout(source.getTimeout());
        target.setCustomSettings(new LinkedHashMap<>(source.getCustomSettings()));
        return target;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (!Objects.isNull(value)) {
            target.put(key, value);
        }
    }

    private ChatMemory createFromPlugin(String type, Map<String, Object> config) {
        Object provider = loadProviders().get(type);
        if (provider == null) {
            return null;
        }

        try {
            Method createMethod = provider.getClass().getMethod("create", Map.class);
            Object memory = createMethod.invoke(provider, config);
            if (memory instanceof ChatMemory chatMemory) {
                return chatMemory;
            }
            throw new IllegalStateException("provider 返回值不是 ChatMemory: " + provider.getClass().getName());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("记忆插件创建失败: type=" + type
                    + ", provider=" + provider.getClass().getName(), e);
        }
    }

    private Map<String, Object> loadProviders() {
        Map<String, Object> cache = discoveredProviders;
        if (cache != null) {
            return cache;
        }

        synchronized (this) {
            if (discoveredProviders != null) {
                return discoveredProviders;
            }

            Map<String, ProviderHolder> selectedProviders = new LinkedHashMap<>();
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try {
                Enumeration<URL> resources = classLoader.getResources(PROVIDER_RESOURCE);
                while (resources.hasMoreElements()) {
                    loadProvidersFromResource(resources.nextElement(), classLoader, selectedProviders);
                }
            } catch (IOException e) {
                throw new IllegalStateException("加载记忆插件资源失败: " + PROVIDER_RESOURCE, e);
            }

            Map<String, Object> providers = new LinkedHashMap<>();
            for (Map.Entry<String, ProviderHolder> entry : selectedProviders.entrySet()) {
                providers.put(entry.getKey(), entry.getValue().provider());
            }
            discoveredProviders = Collections.unmodifiableMap(providers);
            log.info("已发现 memory 插件: {}", discoveredProviders.keySet());
            return discoveredProviders;
        }
    }

    private void loadProvidersFromResource(URL resource,
            ClassLoader classLoader,
            Map<String, ProviderHolder> selectedProviders) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String className = sanitizeProviderLine(line);
                if (className == null) {
                    continue;
                }
                Object provider = instantiateProvider(className, classLoader);
                String type = readProviderType(provider);
                int priority = readProviderPriority(provider);
                ProviderHolder current = selectedProviders.get(type);
                if (current == null || priority > current.priority()) {
                    selectedProviders.put(type, new ProviderHolder(provider, priority));
                } else {
                    log.warn("忽略低优先级 memory 插件: type={}, provider={}, priority={}",
                            type, className, priority);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取记忆插件声明失败: " + resource, e);
        }
    }

    private Object instantiateProvider(String className, ClassLoader classLoader) {
        try {
            Class<?> providerClass = Class.forName(className, true, classLoader);
            return providerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("实例化记忆插件失败: " + className, e);
        }
    }

    private String readProviderType(Object provider) {
        try {
            Method method = provider.getClass().getMethod("getType");
            Object result = method.invoke(provider);
            if (result == null) {
                throw new IllegalStateException("memory 插件 getType() 返回 null: " + provider.getClass().getName());
            }
            return result.toString().trim();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("memory 插件缺少 getType() 方法: " + provider.getClass().getName(), e);
        }
    }

    private int readProviderPriority(Object provider) {
        try {
            Method method = provider.getClass().getMethod("getPriority");
            Object result = method.invoke(provider);
            if (result instanceof Number number) {
                return number.intValue();
            }
            return 0;
        } catch (NoSuchMethodException ignored) {
            return 0;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 memory 插件优先级失败: " + provider.getClass().getName(), e);
        }
    }

    private String sanitizeProviderLine(String line) {
        if (line == null) {
            return null;
        }
        int commentIndex = line.indexOf('#');
        String trimmed = commentIndex >= 0 ? line.substring(0, commentIndex).trim() : line.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ProviderHolder(Object provider, int priority) {
    }
}
