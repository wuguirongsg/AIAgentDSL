package com.agentdsl.memory.hypergraph.model;

import java.time.Instant;
import java.util.List;

/**
 * 超图中的基础节点，表示实体、事件、概念或情绪。
 */
public record MemoryNode(
        String id,
        String memoryId,
        String content,
        NodeType nodeType,
        List<Float> embedding,
        Instant createdAt,
        Instant lastAccessedAt,
        int accessCount) {

    public enum NodeType {
        ENTITY,
        EVENT,
        CONCEPT,
        EMOTION
    }
}
