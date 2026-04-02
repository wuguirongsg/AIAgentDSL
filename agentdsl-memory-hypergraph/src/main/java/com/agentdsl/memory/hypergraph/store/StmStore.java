package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 短期记忆存储接口。
 *
 * <p>STM 存储最近的聊天消息，提供 O(1) 读写和自动淘汰机制。
 * 默认实现 {@link InMemoryStmStore} 使用 ConcurrentHashMap + ConcurrentLinkedDeque。</p>
 *
 * <h3>淘汰策略</h3>
 * <ul>
 *   <li>TTL 过期淘汰 — {@link #evictExpired} 淘汰超过存活时间的超边</li>
 *   <li>容量溢出淘汰 — {@link #evictOverflow} 淘汰最低权重的超边</li>
 * </ul>
 *
 * @see InMemoryStmStore
 * @see HyperEdge
 */
public interface StmStore {

    /**
     * 写入一条新的超边到 STM。
     *
     * @param edge 超边（通常来自 HypergraphMemoryStore.toEdge()）
     */
    void add(HyperEdge edge);

    /**
     * 获取当前所有超边的快照（按写入顺序）。
     * <p>返回的是副本，对返回列表的修改不影响 STM 内部状态。</p>
     *
     * @return 超边列表（不可变视图）
     */
    List<HyperEdge> snapshot();

    /**
     * 获取当前所有超边（等价于 snapshot）。
     *
     * @return 超边列表
     */
    List<HyperEdge> findAll();

    /**
     * 淘汰超出指定容量的超边（按 FIFO 顺序）。
     * <p>被淘汰的超边需要后续压缩到 LTM。</p>
     *
     * @param maxSize 最大容量
     * @return 被淘汰的超边列表
     */
    List<HyperEdge> evictOverflow(int maxSize);

    /**
     * 淘汰超过 TTL 的过期超边。
     * <p>根据 createdAt 与当前时间的差值判断是否过期。</p>
     *
     * @param now 当前时间
     * @param ttl 存活时间（如 24 小时）
     * @return 被淘汰的超边列表
     */
    List<HyperEdge> evictExpired(Instant now, Duration ttl);

    /**
     * 移除指定 ID 的超边。
     *
     * @param edgeId 超边 ID
     */
    void remove(String edgeId);

    /**
     * 检查指定 ID 的超边是否仍在 STM 中。
     *
     * <p>用于 ScoringBatchWorker 在更新前确认超边未被迁移到 LTM，避免幽灵边写回。</p>
     *
     * @param edgeId 超边 ID
     * @return true 如果超边存在于 STM
     */
    default boolean exists(String edgeId) {
        return findAll().stream().anyMatch(e -> e.id().equals(edgeId));
    }

    /**
     * 清空所有 STM 数据。
     */
    void clear();
}
