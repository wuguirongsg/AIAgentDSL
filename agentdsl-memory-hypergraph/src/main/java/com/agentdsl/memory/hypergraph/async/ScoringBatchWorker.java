package com.agentdsl.memory.hypergraph.async;

import com.agentdsl.memory.hypergraph.config.AsyncConfig;
import com.agentdsl.memory.hypergraph.engine.ImportanceScorer;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.MemoryTier;
import com.agentdsl.memory.hypergraph.store.StmStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 批量重要度精确评分后台线程（v1.2 新增）。
 *
 * <p>超边写入 STM 时已完成规则预评分，此 Worker 在后台批量用精确评分更新。
 * 对话路径不感知此过程——STM 中超边先以规则预评分存在，后台静默修正。</p>
 *
 * <p>优化策略：精确分与预评分差距 ≤ 0.1 时跳过更新，减少不必要写入。</p>
 */
public class ScoringBatchWorker {

    private static final Logger log = Logger.getLogger(ScoringBatchWorker.class.getName());

    private final IngestQueue ingestQueue;
    private final StmStore stmStore;
    private final ImportanceScorer scorer;
    private final GraphUpdateWorker graphUpdateWorker;
    private final AsyncConfig asyncConfig;
    private final ScheduledExecutorService scheduler;

    public ScoringBatchWorker(IngestQueue ingestQueue,
            StmStore stmStore,
            ImportanceScorer scorer,
            GraphUpdateWorker graphUpdateWorker,
            AsyncConfig asyncConfig) {
        this.ingestQueue = ingestQueue;
        this.stmStore = stmStore;
        this.scorer = scorer;
        this.graphUpdateWorker = graphUpdateWorker;
        this.asyncConfig = asyncConfig;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hypergraph-scoring-batch");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        long interval = asyncConfig.scoringIntervalMinutes();
        scheduler.scheduleAtFixedRate(this::processBatch, interval, interval, TimeUnit.MINUTES);
    }

    private void processBatch() {
        List<IngestQueue.IngestTask> batch = new ArrayList<>();
        ingestQueue.getQueue().drainTo(batch, asyncConfig.scoringBatchSize());
        if (batch.isEmpty()) {
            return;
        }

        List<HyperEdge> updated = new ArrayList<>();
        for (IngestQueue.IngestTask task : batch) {
            try {
                double preciseScore = scorer.score(task.rawText());
                // 差距 > 0.1 才更新，减少不必要写入
                if (Math.abs(preciseScore - task.edge().importance()) > 0.1) {
                    // 检查超边是否仍在 STM，防止将已迁移到 LTM 的超边写回 STM（幽灵边）
                    if (!stmStore.exists(task.edge().id())) {
                        log.fine("跳过重评分（超边已迁移到 LTM）: edgeId=" + task.edge().id());
                        continue;
                    }
                    HyperEdge rescored = rescoreEdge(task.edge(), preciseScore);
                    stmStore.remove(task.edge().id());
                    stmStore.add(rescored);
                    updated.add(rescored);
                } else {
                    updated.add(task.edge());
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "精确评分失败 edgeId=" + task.edge().id(), e);
                updated.add(task.edge());
            }
        }

        // 通知图索引异步更新
        graphUpdateWorker.enqueue(updated);
        log.fine("批量评分完成，处理 " + batch.size() + " 条，更新 " + updated.size() + " 条");
    }

    private HyperEdge rescoreEdge(HyperEdge edge, double newImportance) {
        return new HyperEdge(
                edge.id(), edge.memoryId(), edge.messageType(), edge.rawContent(),
                edge.toolRequestId(), edge.toolName(),
                edge.weight(), newImportance,
                MemoryTier.STM, 0,
                edge.compressedSummary(), edge.archivePointers(), edge.nodeIds(),
                edge.accessCount(), edge.anchor(),
                edge.createdAt(), Instant.now(),
                edge.emotionTag(), edge.contextTags(),
                edge.linkedEdgeIds(), edge.metaEdgeId());
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
