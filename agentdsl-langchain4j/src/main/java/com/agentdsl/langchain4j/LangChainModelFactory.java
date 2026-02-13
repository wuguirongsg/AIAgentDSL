package com.agentdsl.langchain4j;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.ModelSpec;
import dev.langchain4j.model.chat.ChatModel;
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
            default -> throw new DslRuntimeException("ADSL-021",
                    "不支持的模型 Provider: " + provider
                            + "。当前支持: openai, ollama, deepseek");
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
