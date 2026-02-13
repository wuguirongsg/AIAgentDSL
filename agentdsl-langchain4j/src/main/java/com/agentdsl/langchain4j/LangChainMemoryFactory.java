package com.agentdsl.langchain4j;

import com.agentdsl.core.spec.MemorySpec;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LangChain4j 记忆工厂。
 * 根据 MemorySpec 创建对应的 ChatMemory 实例。
 */
public class LangChainMemoryFactory {

    private static final Logger log = LoggerFactory.getLogger(LangChainMemoryFactory.class);

    /**
     * 根据 MemorySpec 创建 ChatMemory。
     * 如果 spec 为 null，返回默认的 10 条消息窗口记忆。
     */
    public ChatMemory create(MemorySpec spec) {
        if (spec == null) {
            log.debug("未配置记忆，使用默认 MessageWindowChatMemory(10)");
            return MessageWindowChatMemory.withMaxMessages(10);
        }

        String type = spec.getType();
        log.info("创建记忆: type={}", type);

        return switch (type) {
            case "message_window" -> MessageWindowChatMemory.withMaxMessages(
                    spec.getMaxMessages() != null ? spec.getMaxMessages() : 20);
            case "token_window" -> {
                int maxTokens = spec.getMaxTokens() != null ? spec.getMaxTokens() : 4000;
                yield TokenWindowChatMemory.withMaxTokens(maxTokens,
                        new OpenAiTokenCountEstimator("gpt-4"));
            }
            default -> {
                log.warn("未知的记忆类型: {}，回退到 message_window", type);
                yield MessageWindowChatMemory.withMaxMessages(20);
            }
        };
    }
}
