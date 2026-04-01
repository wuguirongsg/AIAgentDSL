package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.config.DecayConfig;
import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.graph.MemoryGraphExtractor;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.MemoryNode;
import com.agentdsl.memory.hypergraph.model.MemoryTier;
import com.agentdsl.memory.hypergraph.store.LtmGraphIndex;
import com.agentdsl.memory.hypergraph.store.LtmStore;
import com.agentdsl.memory.hypergraph.store.StmStore;
import com.agentdsl.memory.hypergraph.store.VectorArchiveStore;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 后台记忆整合引擎，模拟人脑的"睡眠整合"机制。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>STM → LTM 压缩：对低权重超边生成摘要并迁移到 SQLite 持久化</li>
 *   <li>LTM → Archive 归档：对极低权重超边标记归档，原始碎片移入冷库</li>
 *   <li>TTL 过期淘汰：定期清理超过存活时间的 STM 记忆</li>
 *   <li>容量溢出控制：STM 超出 maxEdges 时淘汰最低权重的超边</li>
 * </ul>
 *
 * <h3>压缩流程（moveToLongTermMemory）</h3>
 * <pre>
 *   HyperEdge (STM)
 *     → archiveStore.archive()  // 原始碎片存入冷库
 *     → graphExtractor.extractNodes()  // 提取 MemoryNode
 *     → ltmStore.saveNodes()  // 持久化节点
 *     → decayEngine.computeWeight()  // 计算当前权重
 *     → ltmStore.findRelatedEdges()  // 查找关联边（超图构建）
 *     → summaryCompressor.compress()  // 生成摘要
 *     → ltmStore.save(ltmEdge)  // 写入 LTM
 *     → 更新关联边的 linkedEdgeIds  // 双向链接
 * </pre>
 *
 * <h3>调度策略</h3>
 * <ul>
 *   <li>后台定时任务：按 configurable intervalHours 执行（默认 6 小时）</li>
 *   <li>即时触发：每次 add() 后检查过期和溢出</li>
 *   <li>手动触发：通过 consolidate() 方法</li>
 * </ul>
 *
 * @see DecayEngine
 * @see SummaryCompressor
 * @see MemoryGraphExtractor
 */
public class ConsolidationEngine {

    private final HypergraphMemoryConfig config;
    private final StmStore stmStore;
    private final LtmStore ltmStore;
    private final LtmGraphIndex ltmGraphIndex;
    private final VectorArchiveStore archiveStore;
    private final DecayEngine decayEngine;
    private final SummaryCompressor summaryCompressor;
    private final MemoryGraphExtractor graphExtractor;
    private final AbstractionEngine abstractionEngine;
    private final ScheduledExecutorService scheduler;

    public ConsolidationEngine(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore) {
        this(config, stmStore, ltmStore, null, archiveStore,
                new DecayEngine(config.decay() != null ? config.decay() : DecayConfig.defaults()),
                SummaryCompressorFactory.create(config.ltm()),
                new MemoryGraphExtractor());
    }

    public ConsolidationEngine(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            DecayEngine decayEngine,
            SummaryCompressor summaryCompressor) {
        this(config, stmStore, ltmStore, null, archiveStore, decayEngine, summaryCompressor,
                new MemoryGraphExtractor());
    }

    public ConsolidationEngine(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            DecayEngine decayEngine,
            SummaryCompressor summaryCompressor,
            MemoryGraphExtractor graphExtractor) {
        this(config, stmStore, ltmStore, null, archiveStore, decayEngine, summaryCompressor, graphExtractor);
    }

    public ConsolidationEngine(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            LtmGraphIndex ltmGraphIndex,
            VectorArchiveStore archiveStore,
            DecayEngine decayEngine,
            SummaryCompressor summaryCompressor,
            MemoryGraphExtractor graphExtractor) {
        this.config = config;
        this.stmStore = stmStore;
        this.ltmStore = ltmStore;
        this.ltmGraphIndex = ltmGraphIndex;
        this.archiveStore = archiveStore;
        this.decayEngine = decayEngine;
        this.summaryCompressor = summaryCompressor;
        this.graphExtractor = graphExtractor;
        this.abstractionEngine = ltmGraphIndex != null
                ? new AbstractionEngine(config.memoryId(), ltmStore, ltmGraphIndex)
                : null;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "hypergraph-memory-consolidation");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 消息写入后的即时整合检查。
     * <p>每次 add() 调用后触发，执行：</p>
     * <ol>
     *   <li>淘汰超过 TTL 的过期 STM 记忆并压缩到 LTM</li>
     *   <li>若 STM 容量超出 maxEdges，淘汰最低权重的超边</li>
     * </ol>
     */
    public void onAfterAdd() {
        drainExpiredStm(Instant.now()).forEach(this::moveToLongTermMemory);
        List<HyperEdge> overflow = stmStore.evictOverflow(config.stm().maxEdges());
        overflow.forEach(this::moveToLongTermMemory);
    }

    /**
     * 手动触发一次完整整合。
     * <p>执行以下步骤：</p>
     * <ol>
     *   <li>淘汰过期的 STM 记忆</li>
     *   <li>对低权重 STM 超边执行衰减检查，低于阈值则压缩到 LTM</li>
     *   <li>对低权重 LTM 超边标记归档</li>
     * </ol>
     */
    public void consolidate() {
        Instant now = Instant.now();
        drainExpiredStm(now).forEach(this::moveToLongTermMemory);
        stmStore.findAll().stream()
                .filter(edge -> decayEngine.shouldCompress(edge, now))
                .toList()
                .forEach(edge -> {
                    stmStore.remove(edge.id());
                    moveToLongTermMemory(edge);
                });

        ltmStore.findAll(config.memoryId()).stream()
                .filter(edge -> edge.tier() == MemoryTier.LTM)
                .filter(edge -> decayEngine.shouldArchive(edge, now))
                .forEach(edge -> ltmStore.markArchived(edge.id()));

        // Level-1 → Level-2 主题聚类（需要图索引支持）
        if (abstractionEngine != null) {
            abstractionEngine.abstractClusters();
        }
    }

    /**
     * 启动后台定时整合任务。
     *
     * @param interval 执行间隔
     * @param unit     时间单位
     */
    public void start(long interval, TimeUnit unit) {
        scheduler.scheduleAtFixedRate(this::consolidate, interval, interval, unit);
    }

    /**
     * 停止后台整合调度器。
     * <p>调用 shutdownNow() 立即终止正在执行的任务。</p>
     */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * 排出超过 TTL 的过期 STM 记忆。
     *
     * @param now 当前时间
     * @return 被淘汰的超边列表（需后续压缩到 LTM）
     */
    public List<HyperEdge> drainExpiredStm(Instant now) {
        return stmStore.evictExpired(now, Duration.ofHours(config.stm().ttlHours()));
    }

    private void moveToLongTermMemory(HyperEdge edge) {
        Instant now = Instant.now();
        List<String> archivePointers = archiveStore.archive(edge);

        // 持久化节点对象（MemoryNode 不在 HyperEdge 中，需要重新提取）；
        // contextTags/nodeIds/emotionTag 已在 STM 入边时抽取完毕，直接复用，无需重复计算。
        List<MemoryNode> nodes = graphExtractor.extractNodes(
                edge.memoryId(), edge.messageText(), edge.createdAt());
        ltmStore.saveNodes(edge.memoryId(), nodes);

        double weight = decayEngine.computeWeight(edge, now);

        // 先构造一个不带 links 的 probe，用 edge 已有的 nodeIds/contextTags 寻找相邻记忆边。
        HyperEdge probe = new HyperEdge(
                edge.id(), edge.memoryId(), edge.messageType(), edge.rawContent(),
                edge.toolRequestId(), edge.toolName(),
                weight, edge.importance(), MemoryTier.LTM,
                1, null, archivePointers,
                edge.nodeIds(),
                edge.accessCount(), edge.anchor(), edge.createdAt(), now,
                edge.emotionTag(), edge.contextTags(),
                List.of(), null);

        List<HyperEdge> relatedEdges = ltmStore.findRelatedEdges(edge.memoryId(), probe, 5);
        List<String> linkedEdgeIds = relatedEdges.stream().map(HyperEdge::id).distinct().toList();

        // 正式落库时写入摘要、节点、标签和关联边，形成可继续扩展的超图骨架。
        HyperEdge ltmEdge = new HyperEdge(
                edge.id(), edge.memoryId(), edge.messageType(), edge.rawContent(),
                edge.toolRequestId(), edge.toolName(),
                weight, edge.importance(), MemoryTier.LTM,
                1,
                summaryCompressor.compress(edge.messageText(), config.summaryMaxLength()),
                archivePointers,
                edge.nodeIds(),
                edge.accessCount(), edge.anchor(), edge.createdAt(), now,
                edge.emotionTag(), edge.contextTags(),
                linkedEdgeIds, null);
        ltmStore.save(ltmEdge);

        // 写入 LTM 图索引【v1.1 新增】
        if (ltmGraphIndex != null) {
            ltmGraphIndex.addEdge(ltmEdge);
        }

        // 关系是双向的，命中关联的旧边也补回当前边 id，避免只形成单向 dangling link。
        for (HyperEdge related : relatedEdges) {
            List<String> mergedLinks = java.util.stream.Stream.concat(
                    related.linkedEdgeIds().stream(),
                    java.util.stream.Stream.of(ltmEdge.id()))
                    .distinct()
                    .toList();
            ltmStore.replaceLinkedEdgeIds(related.id(), mergedLinks);
        }

        // 关联迁移：通知图索引更新邻居到新 LTM 超边的关联权重【v1.1 新增】
        if (ltmGraphIndex != null) {
            migrateLinks(edge, ltmEdge);
        }
    }

    /**
     * 关联迁移：当一条超边从 STM 迁移到 LTM 时，更新图索引中所有邻居的关联权重。
     *
     * <p>STM 和 LTM 共享同一个超边 ID，迁移后 ID 不变，
     * 因此邻居的 linkedEdgeIds 内容不需要替换，
     * 但图索引中的边类型需要从"STM-STM"升级为"STM-LTM"或"LTM-LTM"。</p>
     */
    private void migrateLinks(HyperEdge oldStmEdge, HyperEdge newLtmEdge) {
        if (oldStmEdge.linkedEdgeIds() == null) {
            return;
        }
        for (String neighborId : oldStmEdge.linkedEdgeIds()) {
            stmStore.findAll().stream()
                    .filter(n -> n.id().equals(neighborId))
                    .findFirst()
                    .ifPresent(neighbor ->
                            ltmGraphIndex.updateEdgeWeight(neighborId, newLtmEdge.id(),
                                    computeLinkWeight(neighbor, newLtmEdge)));
            ltmStore.findById(neighborId).ifPresent(neighbor ->
                    ltmGraphIndex.updateEdgeWeight(neighborId, newLtmEdge.id(),
                            computeLinkWeight(neighbor, newLtmEdge)));
        }
    }

    private double computeLinkWeight(HyperEdge a, HyperEdge b) {
        List<String> tagsA = a.contextTags() != null ? a.contextTags() : List.of();
        List<String> tagsB = b.contextTags() != null ? b.contextTags() : List.of();
        long shared = tagsA.stream().filter(tagsB::contains).count();
        long total = tagsA.size() + tagsB.size() - shared;
        return total == 0 ? 0.0 : (double) shared / total;
    }
}
