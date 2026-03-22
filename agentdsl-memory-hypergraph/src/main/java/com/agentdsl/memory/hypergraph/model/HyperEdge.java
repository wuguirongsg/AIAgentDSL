package com.agentdsl.memory.hypergraph.model;

import java.time.Instant;
import java.util.List;

/**
 * 超图中的记忆超边。
 * 在现有 episode 记忆基础上，引入 nodeIds / weight / emotion / context / linked edges，
 * 使其能够表达真正的多元关系网络。
 *
 * 建议通过静态工厂方法创建实例，避免直接传入 19 个位置参数。
 */
public record HyperEdge(
        String id,
        String memoryId,
        String messageType,
        String rawContent,
        String toolRequestId,
        String toolName,
        double weight,
        double importance,
        MemoryTier tier,
        String compressedSummary,
        List<String> archivePointers,
        List<String> nodeIds,
        int accessCount,
        boolean anchor,
        Instant createdAt,
        Instant lastAccessedAt,
        EmotionTag emotionTag,
        List<String> contextTags,
        List<String> linkedEdgeIds) {

    public String messageText() {
        return rawContent;
    }

    /**
     * 创建一条 STM 超边（消息入库时使用）。
     * weight 初始为 1.0，archivePointers/linkedEdgeIds 为空列表，accessCount 为 1。
     *
     * @param anchor 是否为锚点（高重要度消息不衰减，如 systemPrompt）
     */
    public static HyperEdge forStm(String id, String memoryId, String messageType,
            String content, String toolRequestId, String toolName,
            double importance, boolean anchor,
            EmotionTag emotionTag, List<String> contextTags, List<String> nodeIds) {
        Instant now = Instant.now();
        return new HyperEdge(
                id, memoryId, messageType, content,
                toolRequestId, toolName,
                1.0, importance,
                MemoryTier.STM,
                null, List.of(), nodeIds,
                1, anchor,
                now, now,
                emotionTag, contextTags, List.of());
    }
}
