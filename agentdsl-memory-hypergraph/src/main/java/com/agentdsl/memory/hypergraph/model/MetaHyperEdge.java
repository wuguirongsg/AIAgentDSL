package com.agentdsl.memory.hypergraph.model;

import java.time.Instant;
import java.util.List;

/**
 * 元超边：LTM Level-2 的核心单元。
 *
 * 代表多条 LTM 摘要超边聚类后的主题抽象，是"记忆的记忆"。
 * 例如：10条关于"Agent DSL 架构讨论"的 LTM 摘要超边
 *       → 聚合为一条元超边："Agent DSL 架构演化历程（2024-03 ~ 2024-06）"
 */
public record MetaHyperEdge(
        String id,
        List<String> memberEdgeIds,
        String themeSummary,
        List<String> linkedMetaEdgeIds,
        List<String> contextTags,
        double cohesion,
        Instant createdAt,
        Instant updatedAt) {
}
