package com.agentdsl.memory.hypergraph.engine;

import dev.langchain4j.model.chat.ChatModel;

/**
 * 使用真实 ChatModel 对记忆做摘要压缩。
 * 调用失败或返回空文本时，自动回退到本地摘要器。
 */
public class ChatModelSummaryCompressor implements SummaryCompressor {

    private static final String PROMPT = """
            将以下对话记忆片段压缩为 1-2 句话的摘要。
            保留最重要的信息：核心事实、关键决策、重要偏好、重要情绪。
            输出限制在 %d 个字符内。

            原始内容：
            %s

            只输出压缩后的摘要，不要任何前缀。
            """;

    private final ChatModel chatModel;
    private final SummaryCompressor fallback;

    public ChatModelSummaryCompressor(ChatModel chatModel, SummaryCompressor fallback) {
        this.chatModel = chatModel;
        this.fallback = fallback != null ? fallback : new HeuristicSummaryCompressor();
    }

    @Override
    public String compress(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String prompt = PROMPT.formatted(Math.max(24, maxLength), content);
        try {
            String result = ChatModelPromptRunner.generate(chatModel, prompt);
            if (result == null || result.isBlank()) {
                return fallback.compress(content, maxLength);
            }
            return result.length() <= maxLength ? result : result.substring(0, maxLength) + "...";
        } catch (RuntimeException ex) {
            return fallback.compress(content, maxLength);
        }
    }
}
