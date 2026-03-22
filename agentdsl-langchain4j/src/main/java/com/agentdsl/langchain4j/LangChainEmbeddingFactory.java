package com.agentdsl.langchain4j;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.ModelSpec;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Locale;

/**
 * LangChain4j EmbeddingModel 工厂。
 * memory/rag 等需要嵌入模型的场景可复用该工厂，避免各处重复解析 provider。
 */
public class LangChainEmbeddingFactory {

    private static final Logger log = LoggerFactory.getLogger(LangChainEmbeddingFactory.class);

    public EmbeddingModel create(ModelSpec agentModelSpec, String embeddingModelName) {
        String modelName = embeddingModelName != null ? embeddingModelName.trim() : "";
        if (modelName.isBlank()) {
            log.info("未指定 embeddingModel，使用内置 AllMiniLmL6V2");
            return new AllMiniLmL6V2EmbeddingModel();
        }

        String normalized = modelName.toLowerCase(Locale.ROOT);
        if (normalized.equals("all-minilm-l6-v2") || normalized.equals("allminilm-l6-v2")) {
            log.info("使用内置嵌入模型: {}", modelName);
            return new AllMiniLmL6V2EmbeddingModel();
        }

        if (agentModelSpec == null || agentModelSpec.getProvider() == null || agentModelSpec.getProvider().isBlank()) {
            throw new DslRuntimeException("ADSL-054",
                    "embeddingModel '" + modelName + "' 需要可解析的主模型 provider 上下文");
        }

        String provider = agentModelSpec.getProvider().trim().toLowerCase(Locale.ROOT);
        log.info("创建嵌入模型: provider={}, modelName={}", provider, modelName);
        return switch (provider) {
            case "ollama" -> createOllama(agentModelSpec, modelName);
            case "openai" -> createOpenAi(agentModelSpec, modelName);
            case "deepseek" -> createOpenAiCompatible(agentModelSpec, modelName,
                    "https://api.deepseek.com/v1", "DEEPSEEK_API_KEY");
            case "kimi", "moonshot" -> createOpenAiCompatible(agentModelSpec, modelName,
                    "https://api.moonshot.cn/v1", "MOONSHOT_API_KEY");
            case "doubao" -> createOpenAiCompatible(agentModelSpec, modelName,
                    "https://ark.cn-beijing.volces.com/api/v3", "DOUBAO_API_KEY");
            case "qwen" -> createOpenAiCompatible(agentModelSpec, modelName,
                    "https://dashscope.aliyuncs.com/compatible-mode/v1", "DASHSCOPE_API_KEY");
            case "zhipu", "glm" -> createOpenAiCompatible(agentModelSpec, modelName,
                    "https://open.bigmodel.cn/api/paas/v4/", "ZHIPU_API_KEY");
            case "minimax" -> createOpenAiCompatible(agentModelSpec, modelName,
                    "https://api.minimax.chat/v1", "MINIMAX_API_KEY");
            default -> throw new DslRuntimeException("ADSL-054",
                    "当前 provider '" + agentModelSpec.getProvider() + "' 暂不支持 embeddingModel '" + modelName + "'");
        };
    }

    private EmbeddingModel createOllama(ModelSpec spec, String modelName) {
        String baseUrl = spec.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }

        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(spec.getTimeout()))
                .build();
    }

    private EmbeddingModel createOpenAi(ModelSpec spec, String modelName) {
        String apiKey = spec.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnv("OPENAI_API_KEY");
        }

        var builder = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(spec.getTimeout()));

        if (spec.getBaseUrl() != null && !spec.getBaseUrl().isBlank()) {
            builder.baseUrl(spec.getBaseUrl());
        }

        return builder.build();
    }

    private EmbeddingModel createOpenAiCompatible(ModelSpec spec,
            String modelName,
            String defaultBaseUrl,
            String envKey) {
        String apiKey = spec.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnv(envKey);
        }
        String baseUrl = spec.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = defaultBaseUrl;
        }

        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(spec.getTimeout()))
                .build();
    }

    private static String resolveEnv(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key);
        }
        if (value == null) {
            throw new DslRuntimeException("ADSL-012", "环境变量未找到: " + key + "。请设置该环境变量后重试。");
        }
        return value;
    }
}
