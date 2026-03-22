package com.agentdsl.memory.hypergraph.engine;

import dev.langchain4j.model.chat.ChatModel;

import java.util.List;

/**
 * 使用真实 ChatModel 对 Archive 碎片做情境重建。
 * 若模型调用失败，则回退到启发式重建。
 */
public class ChatModelMemoryReconstructor implements MemoryReconstructor {

    private static final String PROMPT = """
            你在执行长期记忆重建。
            请基于查询、摘要和记忆碎片，重建最相关的历史上下文。
            尽量还原关键细节；若信息不足，请明确说明不确定之处。

            查询：
            %s

            摘要：
            %s

            记忆碎片：
            %s

            只输出重建后的内容，不要添加额外前缀。
            """;

    private final ChatModel chatModel;
    private final MemoryReconstructor fallback;

    public ChatModelMemoryReconstructor(ChatModel chatModel, MemoryReconstructor fallback) {
        this.chatModel = chatModel;
        this.fallback = fallback != null ? fallback : new HeuristicMemoryReconstructor();
    }

    @Override
    public String reconstruct(String query, String summary, List<String> fragments) {
        String fragmentsText = (fragments == null || fragments.isEmpty())
                ? "(无额外碎片，仅可参考摘要)"
                : String.join("\n---\n", fragments);
        try {
            String result = ChatModelPromptRunner.generate(chatModel,
                    PROMPT.formatted(nullSafe(query), nullSafe(summary), fragmentsText));
            if (result == null || result.isBlank()) {
                return fallback.reconstruct(query, summary, fragments != null ? fragments : List.of());
            }
            return result;
        } catch (RuntimeException ex) {
            return fallback.reconstruct(query, summary, fragments != null ? fragments : List.of());
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
