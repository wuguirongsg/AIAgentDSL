package com.agentdsl.langchain4j;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.ModelSpec;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j 模型工厂。
 * 根据 ModelSpec 创建对应的 ChatModel 实例。
 * 支持通过 SPI 扩展自定义 Provider。
 */
public class LangChainModelFactory {

    private static final Logger log = LoggerFactory.getLogger(LangChainModelFactory.class);

    /**
     * 根据 ModelSpec 创建 ChatModel。
     *
     * @param spec 模型配置规范
     * @return ChatModel 实例
     * @throws DslRuntimeException 如果 provider 不支持
     */
    public ChatModel create(ModelSpec spec) {
        String provider = spec.getProvider();
        log.info("创建模型: provider={}, modelName={}", provider, spec.getModelName());

        return switch (provider) {
            case "openai" -> createOpenAi(spec);
            case "ollama" -> createOllama(spec);
            case "deepseek" -> createDeepSeek(spec);
            // 新增国内部分采用 OpenAI 兼容格式的模型
            case "kimi", "moonshot" -> createOpenAiCompatible(spec, "https://api.moonshot.cn/v1", "MOONSHOT_API_KEY");
            case "doubao" -> createOpenAiCompatible(spec, "https://ark.cn-beijing.volces.com/api/v3", "DOUBAO_API_KEY");
            case "qwen" ->
                createOpenAiCompatible(spec, "https://dashscope.aliyuncs.com/compatible-mode/v1", "DASHSCOPE_API_KEY"); // 或
                                                                                                                        // QWEN_API_KEY
            case "zhipu", "glm" ->
                createOpenAiCompatible(spec, "https://open.bigmodel.cn/api/paas/v4/", "ZHIPU_API_KEY");
            case "minimax" -> createOpenAiCompatible(spec, "https://api.minimax.chat/v1", "MINIMAX_API_KEY");

            // 原生支持的其他模型提供商
            case "claude", "anthropic" -> createClaude(spec);
            case "gemini", "google" -> createGemini(spec);

            default -> throw new DslRuntimeException("ADSL-021",
                    "不支持的模型 Provider: " + provider
                            + "。当前支持: openai, ollama, deepseek, kimi, doubao, qwen, zhipu, minimax, claude, gemini");
        };
    }

    private ChatModel createOpenAi(ModelSpec spec) {
        String apiKey = spec.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnv("OPENAI_API_KEY");
        }

        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(spec.getModelName())
                .temperature(spec.getTemperature())
                .topP(spec.getTopP())
                .maxTokens(spec.getMaxTokens())
                .customParameters(spec.getCustomSettings())
                .timeout(Duration.ofSeconds(spec.getTimeout()));

        if (spec.getBaseUrl() != null) {
            builder.baseUrl(spec.getBaseUrl());
        }

        return builder.build();
    }

    private ChatModel createOllama(ModelSpec spec) {
        String baseUrl = spec.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }

        var builder = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(spec.getModelName())
                .temperature(spec.getTemperature())
                .timeout(Duration.ofSeconds(spec.getTimeout()));

        // 处理 Ollama 特定参数
        Map<String, Object> settings = spec.getCustomSettings();
        
        // think 参数 - 控制是否启用思考模式
        if (settings.containsKey("think")) {
            builder.think(toBoolean(settings.get("think")));
        }
        
        // returnThinking 参数 - 是否返回思考过程
        if (settings.containsKey("returnThinking")) {
            builder.returnThinking(toBoolean(settings.get("returnThinking")));
        }
        
        // stop 参数 - 停止词列表
        if (settings.containsKey("stop")) {
            builder.stop(toStringList(settings.get("stop")));
        }
        
        // minP 参数 - 最小概率阈值
        if (settings.containsKey("minP")) {
            builder.minP(toDouble(settings.get("minP")));
        }
        
        // responseFormat 参数 - 响应格式
        if (settings.containsKey("responseFormat")) {
            String format = settings.get("responseFormat").toString();
            if ("json".equalsIgnoreCase(format)) {
                builder.responseFormat(ResponseFormat.JSON);
            } else if ("text".equalsIgnoreCase(format)) {
                builder.responseFormat(ResponseFormat.TEXT);
            }
        }

        return builder.build();
    }

    /**
     * DeepSeek 通过 OpenAI 兼容接口访问。
     */
    private ChatModel createDeepSeek(ModelSpec spec) {
        String apiKey = spec.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnv("DEEPSEEK_API_KEY");
        }
        String baseUrl = spec.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.deepseek.com/v1";
        }

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(spec.getModelName())
                .temperature(spec.getTemperature())
                .topP(spec.getTopP())
                .maxTokens(spec.getMaxTokens())
                .customParameters(spec.getCustomSettings())
                .timeout(Duration.ofSeconds(spec.getTimeout()))
                .build();
    }

    /**
     * 通用的 OpenAI 兼容接口模型工厂方法
     * 用于极速接入那些提供了与 OpenAI API 完全兼容接口的大语言模型。
     */
    private ChatModel createOpenAiCompatible(ModelSpec spec, String defaultBaseUrl, String envKey) {
        String apiKey = spec.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnv(envKey);
        }
        String baseUrl = spec.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = defaultBaseUrl;
        }

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(spec.getModelName())
                .temperature(spec.getTemperature())
                .topP(spec.getTopP())
                .maxTokens(spec.getMaxTokens())
                .customParameters(spec.getCustomSettings())
                .timeout(Duration.ofSeconds(spec.getTimeout()))
                .build();
    }

    private ChatModel createClaude(ModelSpec spec) {
        String apiKey = spec.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnv("ANTHROPIC_API_KEY");
        }

        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(spec.getModelName())
                .temperature(spec.getTemperature())
                .topP(spec.getTopP())
                .maxTokens(spec.getMaxTokens())
                .customParameters(spec.getCustomSettings())
                .timeout(Duration.ofSeconds(spec.getTimeout()))
                .build();
    }

    private ChatModel createGemini(ModelSpec spec) {
        String apiKey = spec.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnv("GEMINI_API_KEY");
        }

        var builder = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(spec.getModelName())
                .temperature(spec.getTemperature())
                .maxOutputTokens(spec.getMaxTokens());

        // topP 转换为 topK (Gemini 使用 topK)
        if (spec.getTopP() != null) {
            builder.topK(spec.getTopP().intValue());
        }

        // 处理 Gemini 特定参数
        Map<String, Object> settings = spec.getCustomSettings();
        
        // seed 参数 - 随机种子
        if (settings.containsKey("seed")) {
            builder.seed(toInteger(settings.get("seed")));
        }
        
        // frequencyPenalty 参数 - 频率惩罚
        if (settings.containsKey("frequencyPenalty")) {
            builder.frequencyPenalty(toDouble(settings.get("frequencyPenalty")));
        }
        
        // presencePenalty 参数 - 存在惩罚
        if (settings.containsKey("presencePenalty")) {
            builder.presencePenalty(toDouble(settings.get("presencePenalty")));
        }
        
        // returnThinking 参数 - 是否返回思考过程
        if (settings.containsKey("returnThinking")) {
            builder.returnThinking(toBoolean(settings.get("returnThinking")));
        }
        
        // sendThinking 参数 - 是否发送思考
        if (settings.containsKey("sendThinking")) {
            builder.sendThinking(toBoolean(settings.get("sendThinking")));
        }
        
        // stopSequences 参数 - 停止序列
        if (settings.containsKey("stopSequences")) {
            builder.stopSequences(toStringList(settings.get("stopSequences")));
        }

        return builder.build();
    }

    /**
     * 从环境变量或系统属性读取值。
     */
    private static String resolveEnv(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = System.getenv(key);
        }
        if (value == null) {
            throw new DslRuntimeException("ADSL-012","环境变量未找到: " + key + "。请设置该环境变量后重试。");
        }
        return value;
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.valueOf(value.toString());
    }

    private static Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.valueOf(value.toString());
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.valueOf(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value == null) return null;
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .toList();
        }
        if (value instanceof String) {
            return Arrays.asList(((String) value).split(","));
        }
        return List.of(value.toString());
    }
}
