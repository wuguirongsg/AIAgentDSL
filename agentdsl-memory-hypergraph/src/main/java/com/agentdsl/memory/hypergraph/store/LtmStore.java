package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.MemoryNode;

import java.util.List;

/**
 * 长期记忆存储接口。
 *
 * <p>LTM 存储压缩后的记忆摘要，持久化到 SQLite 等持久化后端。
 * 默认实现 {@link SQLiteLtmStore} 使用 JDBC 直连 SQLite。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>超边持久化 — {@link #save} 写入压缩后的记忆</li>
 *   <li>语义检索 — {@link #semanticSearch} 基于关键词或向量的近似匹配</li>
 *   <li>超图关系 — {@link #findRelatedEdges} 查找共享节点/标签的关联边</li>
 *   <li>归档标记 — {@link #markArchived} 将超边标记为归档状态</li>
 * </ul>
 *
 * @see SQLiteLtmStore
 * @see HyperEdge
 * @see MemoryNode
 */
public interface LtmStore {

    /**
     * 持久化一条超边到 LTM。
     * <p>如果 ID 已存在，则执行更新（INSERT OR REPLACE）。</p>
     *
     * @param edge 压缩后的超边（含 compressedSummary、archivePointers 等）
     */
    void save(HyperEdge edge);

    /**
     * 查询指定记忆实例的所有超边。
     *
     * @param memoryId 记忆实例 ID（来自 config.memoryId()）
     * @return 超边列表
     */
    List<HyperEdge> findAll(String memoryId);

    /**
     * 基于查询文本的语义检索。
     * <p>当前实现按重要度排序取 topK，由 DeepRecallEngine 做相似度重排。
     * 生产优化可接入向量检索后端。</p>
     *
     * @param memoryId 记忆实例 ID
     * @param query    查询文本
     * @param limit    返回数量上限
     * @return 候选超边列表
     */
    List<HyperEdge> semanticSearch(String memoryId, String query, int limit);

    /**
     * 持久化记忆节点（MemoryNode）。
     * <p>默认空实现，SQLite 实现会写入 hypergraph_nodes 表。</p>
     *
     * @param memoryId 记忆实例 ID
     * @param nodes    节点列表
     */
    default void saveNodes(String memoryId, List<MemoryNode> nodes) {
    }

    /**
     * 查找与指定超边共享节点或标签的关联边。
     * <p>用于构建超图的 linkedEdgeIds 双向链接。</p>
     *
     * @param memoryId 记忆实例 ID
     * @param edge     目标超边
     * @param limit    返回数量上限
     * @return 关联超边列表
     */
    default List<HyperEdge> findRelatedEdges(String memoryId, HyperEdge edge, int limit) {
        return List.of();
    }

    /**
     * 替换指定超边的关联边 ID 列表。
     * <p>用于维护超图的双向链接一致性。</p>
     *
     * @param edgeId         超边 ID
     * @param linkedEdgeIds  新的关联边 ID 列表
     */
    default void replaceLinkedEdgeIds(String edgeId, List<String> linkedEdgeIds) {
    }

    /**
     * 将指定超边标记为归档状态（tier = ARCHIVE）。
     *
     * @param edgeId 超边 ID
     */
    void markArchived(String edgeId);

    /**
     * 清空指定记忆实例的所有数据。
     *
     * @param memoryId 记忆实例 ID
     */
    void clear(String memoryId);
}
