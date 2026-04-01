package com.agentdsl.memory.hypergraph.async;

import com.agentdsl.memory.hypergraph.config.AsyncConfig;
import com.agentdsl.memory.hypergraph.embedding.TextEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.store.LtmGraphIndex;
import com.agentdsl.memory.hypergraph.store.LtmStore;
import com.agentdsl.memory.hypergraph.store.VectorArchiveStore;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 预计算摘要索引——快慢分离架构的核心解耦点（v1.2 新增）。
 *
 * <p>把超图的图关联信息预先计算并嵌入向量检索索引，
 * 使对话路径只需轻量向量检索即可获得图关系增强的摘要。</p>
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>取 LTM Level-1 每条摘要超边</li>
 *   <li>通过图索引找出 N 跳关联邻居</li>
 *   <li>合并邻居摘要，生成"图增强摘要"</li>
 *   <li>写入向量检索索引（对话路径直接读这里）</li>
 *   <li>对元超边主题摘要也执行同样操作</li>
 * </ol>
 *
 * <p>触发时机：ConsolidationEngine 或 AbstractionEngine 完成后触发，
 * 或定期自动运行（默认 30 分钟一次）。</p>
 */
public class SummaryPrecomputer {

    private static final Logger log = Logger.getLogger(SummaryPrecomputer.class.getName());

    private final String memoryId;
    private final LtmStore ltmStore;
    private final LtmGraphIndex ltmGraphIndex;
    private final VectorArchiveStore vectorIndex;
    private final TextEmbeddingGenerator embeddingGenerator;
    private final AsyncConfig asyncConfig;
    private final ScheduledExecutorService scheduler;

    public SummaryPrecomputer(String memoryId,
            LtmStore ltmStore,
            LtmGraphIndex ltmGraphIndex,
            VectorArchiveStore vectorIndex,
            TextEmbeddingGenerator embeddingGenerator,
            AsyncConfig asyncConfig) {
        this.memoryId = memoryId;
        this.ltmStore = ltmStore;
        this.ltmGraphIndex = ltmGraphIndex;
        this.vectorIndex = vectorIndex;
        this.embeddingGenerator = embeddingGenerator;
        this.asyncConfig = asyncConfig;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hypergraph-summary-precompute");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        long interval = asyncConfig.precomputeIntervalMinutes();
        scheduler.scheduleAtFixedRate(this::precompute, interval, interval, TimeUnit.MINUTES);
    }

    /**
     * 手动触发一次预计算（异步，不阻塞调用线程）。
     */
    public void triggerPrecompute() {
        scheduler.submit(this::precompute);
    }

    private void precompute() {
        try {
            List<HyperEdge> ltmEdges = ltmStore.findByLtmLevel(memoryId, 1);
            int hops = asyncConfig.neighborHops();

            for (HyperEdge edge : ltmEdges) {
                try {
                    Set<String> neighborIds = ltmGraphIndex.findNeighbors(edge.id(), hops);
                    List<String> neighborSummaries = neighborIds.stream()
                            .map(ltmStore::findById)
                            .filter(Optional::isPresent)
                            .map(opt -> opt.get().compressedSummary())
                            .filter(s -> s != null && !s.isBlank())
                            .limit(3)
                            .toList();

                    String enrichedSummary = buildEnrichedSummary(edge.compressedSummary(), neighborSummaries);
                    List<Float> embedding = embeddingGenerator.embed(enrichedSummary).vectorAsList();
                    vectorIndex.upsertSummary(edge.id(), enrichedSummary, embedding);
                } catch (Exception e) {
                    log.log(Level.WARNING, "预计算摘要失败 edgeId=" + edge.id(), e);
                }
            }

            // 元超边主题摘要也写入
            ltmStore.findAllMetaEdges().forEach(meta -> {
                try {
                    List<Float> emb = embeddingGenerator.embed(meta.themeSummary()).vectorAsList();
                    vectorIndex.upsertSummary(meta.id(), meta.themeSummary(), emb);
                } catch (Exception e) {
                    log.log(Level.WARNING, "预计算元超边摘要失败 metaId=" + meta.id(), e);
                }
            });

            log.fine("预计算摘要索引完成，处理 " + ltmEdges.size() + " 条 LTM 超边");
        } catch (Exception e) {
            log.log(Level.WARNING, "预计算摘要索引整体失败", e);
        }
    }

    private String buildEnrichedSummary(String core, List<String> neighbors) {
        if (core == null || core.isBlank()) {
            return neighbors.isEmpty() ? "" : String.join("；", neighbors);
        }
        if (neighbors.isEmpty()) {
            return core;
        }
        return core + "【关联：" + String.join("；", neighbors) + "】";
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
