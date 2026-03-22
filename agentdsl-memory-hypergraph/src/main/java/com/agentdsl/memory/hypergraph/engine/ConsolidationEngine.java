package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.config.DecayConfig;
import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.graph.MemoryGraphExtractor;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.MemoryNode;
import com.agentdsl.memory.hypergraph.model.MemoryTier;
import com.agentdsl.memory.hypergraph.store.LtmStore;
import com.agentdsl.memory.hypergraph.store.StmStore;
import com.agentdsl.memory.hypergraph.store.VectorArchiveStore;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsolidationEngine {

    private final HypergraphMemoryConfig config;
    private final StmStore stmStore;
    private final LtmStore ltmStore;
    private final VectorArchiveStore archiveStore;
    private final DecayEngine decayEngine;
    private final SummaryCompressor summaryCompressor;
    private final MemoryGraphExtractor graphExtractor;
    private final ScheduledExecutorService scheduler;

    public ConsolidationEngine(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore) {
        this(config, stmStore, ltmStore, archiveStore,
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
        this(config, stmStore, ltmStore, archiveStore, decayEngine, summaryCompressor, new MemoryGraphExtractor());
    }

    public ConsolidationEngine(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            DecayEngine decayEngine,
            SummaryCompressor summaryCompressor,
            MemoryGraphExtractor graphExtractor) {
        this.config = config;
        this.stmStore = stmStore;
        this.ltmStore = ltmStore;
        this.archiveStore = archiveStore;
        this.decayEngine = decayEngine;
        this.summaryCompressor = summaryCompressor;
        this.graphExtractor = graphExtractor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "hypergraph-memory-consolidation");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void onAfterAdd() {
        drainExpiredStm(Instant.now()).forEach(this::moveToLongTermMemory);
        List<HyperEdge> overflow = stmStore.evictOverflow(config.stm().maxEdges());
        overflow.forEach(this::moveToLongTermMemory);
    }

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
    }

    public void start(long interval, TimeUnit unit) {
        scheduler.scheduleAtFixedRate(this::consolidate, interval, interval, unit);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

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
                null, archivePointers,
                edge.nodeIds(),
                edge.accessCount(), edge.anchor(), edge.createdAt(), now,
                edge.emotionTag(), edge.contextTags(),
                List.of());

        List<HyperEdge> relatedEdges = ltmStore.findRelatedEdges(edge.memoryId(), probe, 5);
        List<String> linkedEdgeIds = relatedEdges.stream().map(HyperEdge::id).distinct().toList();

        // 正式落库时写入摘要、节点、标签和关联边，形成可继续扩展的超图骨架。
        HyperEdge ltmEdge = new HyperEdge(
                edge.id(), edge.memoryId(), edge.messageType(), edge.rawContent(),
                edge.toolRequestId(), edge.toolName(),
                weight, edge.importance(), MemoryTier.LTM,
                summaryCompressor.compress(edge.messageText(), config.summaryMaxLength()),
                archivePointers,
                edge.nodeIds(),
                edge.accessCount(), edge.anchor(), edge.createdAt(), now,
                edge.emotionTag(), edge.contextTags(),
                linkedEdgeIds);
        ltmStore.save(ltmEdge);

        // 关系是双向的，命中关联的旧边也补回当前边 id，避免只形成单向 dangling link。
        for (HyperEdge related : relatedEdges) {
            List<String> mergedLinks = java.util.stream.Stream.concat(
                    related.linkedEdgeIds().stream(),
                    java.util.stream.Stream.of(ltmEdge.id()))
                    .distinct()
                    .toList();
            ltmStore.replaceLinkedEdgeIds(related.id(), mergedLinks);
        }
    }
}
