package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;

import java.util.List;

/**
 * 向量冷库存储接口。
 *
 * <p>Archive 存储记忆的原始细节碎片，供深度回忆时还原完整情境。
 * 默认实现：</p>
 * <ul>
 *   <li>{@link EmbeddingStoreVectorArchiveStore} — 纯内存实现，适合测试</li>
 *   <li>{@link PersistentFileVectorArchiveStore} — 文件本地持久化，适合生产</li>
 * </ul>
 *
 * <h3>数据流</h3>
 * <pre>
 *   STM 超边 → archive() → 生成 archiveId 列表 → 写入 HyperEdge.archivePointers
 *                                                              ↓
 *   深度回忆时 ← retrieve(archivePointers) ← 从冷库还原原始碎片
 * </pre>
 *
 * @see EmbeddingStoreVectorArchiveStore
 * @see PersistentFileVectorArchiveStore
 * @see HyperEdge#archivePointers
 */
public interface VectorArchiveStore {

    /**
     * 将超边的原始内容存入冷库。
     * <p>返回生成的 archiveId 列表，用于后续 retrieve() 还原。</p>
     *
     * @param edge 待归档的超边
     * @return 生成的 archiveId 列表（通常为 1 个或多个碎片 ID）
     */
    List<String> archive(HyperEdge edge);

    /**
     * 根据 archiveId 列表从冷库还原原始碎片。
     *
     * @param archivePointers archiveId 列表（来自 HyperEdge.archivePointers）
     * @return 还原的文本碎片列表
     */
    List<String> retrieve(List<String> archivePointers);

    /**
     * 清空冷库中的所有数据。
     */
    void clear();

    /**
     * 写入或更新预计算摘要（v1.2 新增）。
     *
     * <p>SummaryPrecomputer 把图增强摘要写入此索引，供对话路径轻量检索。
     * 默认空实现，有向量存储后端的实现类应覆盖此方法。</p>
     *
     * @param edgeId          超边或元超边 ID（用作去重键）
     * @param enrichedSummary 图增强摘要文本（含关联超边的上下文）
     * @param embedding       摘要的向量表示
     */
    default void upsertSummary(String edgeId, String enrichedSummary, java.util.List<Float> embedding) {
    }
}
