package com.agentdsl.langchain4j;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.ModelSpec;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

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

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(spec.getModelName())
                .temperature(spec.getTemperature())
                .timeout(Duration.ofSeconds(spec.getTimeout()))
                .build();
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
                .timeout(Duration.ofSeconds(spec.getTimeout()))
                .build();
    }

    private ChatModel createGemini(ModelSpec spec) {
        String apiKey = spec.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnv("GEMINI_API_KEY");
        }

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(spec.getModelName())
                .temperature(spec.getTemperature())
                .topK(spec.getTopP() != null ? spec.getTopP().intValue() : null)
                .maxOutputTokens(spec.getMaxTokens())
                .build();
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
            throw new DslRuntimeException("ADSL-012",
                    "环境变量未找到: " + key + "。请设置该环境变量后重试。");
        }
        return value;
    }
}
