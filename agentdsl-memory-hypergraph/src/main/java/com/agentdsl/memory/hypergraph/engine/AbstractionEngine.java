package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.MetaHyperEdge;
import com.agentdsl.memory.hypergraph.store.LtmGraphIndex;
import com.agentdsl.memory.hypergraph.store.LtmStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 主题抽象引擎：对 LTM Level-1 超边进行语义聚类，生成 Level-2 元超边。
 *
 * <p>这是"记忆的记忆"机制的实现：
 * 多条相关的摘要超边 → 聚合为一条主题元超边，
 * 元超边之间也形成关联网络，构成 LTM Level-2 超图。</p>
 *
 * <p>触发时机：由 ConsolidationEngine 压缩完成后调用，
 * 或由异步层定期独立运行（建议每 24 小时一次）。</p>
 *
 * <p>聚类算法：贪心余弦相似度聚类（基于 contextTags 的 Jaccard 相似度）。
 * 避免引入重量级向量库依赖，后续可替换为真实 embedding 相似度。</p>
 */
public class AbstractionEngine {

    private final String memoryId;
    private final LtmStore ltmStore;
    private final LtmGraphIndex ltmGraphIndex;
    private final double clusterSimilarityThreshold;

    private static final double DEFAULT_THRESHOLD = 0.3;
    private static final int MIN_CLUSTER_SIZE = 2;

    public AbstractionEngine(String memoryId, LtmStore ltmStore, LtmGraphIndex ltmGraphIndex) {
        this(memoryId, ltmStore, ltmGraphIndex, DEFAULT_THRESHOLD);
    }

    public AbstractionEngine(String memoryId, LtmStore ltmStore, LtmGraphIndex ltmGraphIndex,
            double clusterSimilarityThreshold) {
        this.memoryId = memoryId;
        this.ltmStore = ltmStore;
        this.ltmGraphIndex = ltmGraphIndex;
        this.clusterSimilarityThreshold = clusterSimilarityThreshold;
    }

    /**
     * 执行 LTM Level-1 → Level-2 的主题聚类抽象。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>获取所有未归属元超边的 Level-1 超边</li>
     *   <li>基于 contextTags Jaccard 相似度做贪心聚类</li>
     *   <li>为每个有效簇构建元超边并持久化</li>
     *   <li>更新簇内成员的 metaEdgeId 归属</li>
     *   <li>在图索引注册元超边，并建立主题间关联</li>
     * </ol>
     */
    public void abstractClusters() {
        List<HyperEdge> level1Edges = ltmStore.findByLtmLevel(memoryId, 1).stream()
                .filter(e -> e.metaEdgeId() == null)
                .toList();

        if (level1Edges.size() < MIN_CLUSTER_SIZE) {
            return;
        }

        List<List<HyperEdge>> clusters = greedyCluster(level1Edges);

        for (List<HyperEdge> cluster : clusters) {
            if (cluster.size() < MIN_CLUSTER_SIZE) {
                continue;
            }
            MetaHyperEdge metaEdge = buildMetaEdge(cluster);
            ltmStore.saveMetaEdge(metaEdge);
            cluster.forEach(e -> ltmStore.updateMetaEdgeId(e.id(), metaEdge.id()));
            ltmGraphIndex.addMetaEdge(metaEdge);
        }

        linkMetaEdges();
    }

    private List<List<HyperEdge>> greedyCluster(List<HyperEdge> edges) {
        List<List<HyperEdge>> clusters = new ArrayList<>();
        Set<String> assigned = new HashSet<>();

        for (HyperEdge seed : edges) {
            if (assigned.contains(seed.id())) {
                continue;
            }
            List<HyperEdge> cluster = new ArrayList<>();
            cluster.add(seed);
            assigned.add(seed.id());

            for (HyperEdge candidate : edges) {
                if (assigned.contains(candidate.id())) {
                    continue;
                }
                double sim = jaccardSimilarity(seed.contextTags(), candidate.contextTags());
                if (sim >= clusterSimilarityThreshold) {
                    cluster.add(candidate);
                    assigned.add(candidate.id());
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private MetaHyperEdge buildMetaEdge(List<HyperEdge> cluster) {
        List<String> mergedTags = cluster.stream()
                .flatMap(e -> e.contextTags() != null ? e.contextTags().stream() : java.util.stream.Stream.empty())
                .distinct()
                .toList();

        String themeSummary = buildThemeSummary(cluster);

        double avgCohesion = cluster.stream()
                .mapToDouble(HyperEdge::importance)
                .average()
                .orElse(0.5);

        Instant now = Instant.now();
        return new MetaHyperEdge(
                UUID.randomUUID().toString(),
                cluster.stream().map(HyperEdge::id).toList(),
                themeSummary,
                List.of(),
                mergedTags,
                avgCohesion,
                now,
                now);
    }

    private String buildThemeSummary(List<HyperEdge> cluster) {
        String summaries = cluster.stream()
                .map(e -> e.compressedSummary() != null ? e.compressedSummary() : e.messageText())
                .filter(s -> s != null && !s.isBlank())
                .limit(5)
                .collect(Collectors.joining("；"));
        if (summaries.isBlank()) {
            return "主题聚类（" + cluster.size() + " 条记忆）";
        }
        return "【主题摘要】" + summaries;
    }

    private void linkMetaEdges() {
        List<MetaHyperEdge> allMeta = ltmStore.findAllMetaEdges();
        for (int i = 0; i < allMeta.size(); i++) {
            for (int j = i + 1; j < allMeta.size(); j++) {
                MetaHyperEdge a = allMeta.get(i);
                MetaHyperEdge b = allMeta.get(j);
                boolean hasSharedTag = a.contextTags() != null && b.contextTags() != null
                        && a.contextTags().stream().anyMatch(b.contextTags()::contains);
                if (hasSharedTag) {
                    ltmStore.linkMetaEdges(a.id(), b.id());
                    ltmGraphIndex.addMetaLink(a.id(), b.id());
                }
            }
        }
    }

    private double jaccardSimilarity(List<String> tagsA, List<String> tagsB) {
        if (tagsA == null || tagsA.isEmpty() || tagsB == null || tagsB.isEmpty()) {
            return 0.0;
        }
        Set<String> setA = new HashSet<>(tagsA);
        Set<String> setB = new HashSet<>(tagsB);
        long intersection = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }
}
