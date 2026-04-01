package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.embedding.EmbeddingGeneratorFactory;
import com.agentdsl.memory.hypergraph.embedding.TextEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.store.LtmStore;
import com.agentdsl.memory.hypergraph.store.VectorArchiveStore;
import dev.langchain4j.data.embedding.Embedding;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 深度回忆引擎，实现"由浅入深"的记忆还原机制。
 *
 * <h3>回忆流程</h3>
 * <pre>
 *   用户查询 query
 *     → ltmStore.semanticSearch()  // 在 LTM 摘要中语义检索
 *     → 计算 query 与候选超边的相似度（embedding + lexical 双策略）
 *     → 若最高相似度 < deepRecallThreshold → 返回 empty
 *     → archiveStore.retrieve()  // 从冷库加载原始碎片
 *     → reconstructor.reconstruct()  // LLM 重建记忆情境
 *     → 返回重建后的记忆文本
 * </pre>
 *
 * <h3>相似度计算策略</h3>
 * <p>采用 embedding 向量相似度和字面字符相似度的较大值：</p>
 * <ul>
 *   <li>cosineSimilarity — 基于 TextEmbeddingGenerator 的向量余弦相似度</li>
 *   <li>lexicalSimilarity — 基于字符集交集比例的字面匹配（兜底策略）</li>
 * </ul>
 * <p>当 embedding 计算失败时（如模型不可用），自动降级为纯字面匹配。</p>
 *
 * <h3>记忆重建</h3>
 * <p>支持两种重建策略（通过 MemoryReconstructor 接口）：</p>
 * <ul>
 *   <li>HeuristicMemoryReconstructor — 启发式拼接（默认）</li>
 *   <li>ChatModelMemoryReconstructor — LLM 驱动的智能重建（需注入模型）</li>
 * </ul>
 *
 * @see LtmStore#semanticSearch
 * @see VectorArchiveStore#retrieve
 * @see MemoryReconstructor
 */
public class DeepRecallEngine {

    private final HypergraphMemoryConfig config;
    private final LtmStore ltmStore;
    private final VectorArchiveStore archiveStore;
    private final TextEmbeddingGenerator embeddingGenerator;
    private final MemoryReconstructor reconstructor;

    public DeepRecallEngine(HypergraphMemoryConfig config,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore) {
        this(config,
                ltmStore,
                archiveStore,
                EmbeddingGeneratorFactory.create(config.vector()),
                new HeuristicMemoryReconstructor());
    }

    public DeepRecallEngine(HypergraphMemoryConfig config,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            MemoryReconstructor reconstructor) {
        this(config,
                ltmStore,
                archiveStore,
                EmbeddingGeneratorFactory.create(config.vector()),
                reconstructor);
    }

    public DeepRecallEngine(HypergraphMemoryConfig config,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            TextEmbeddingGenerator embeddingGenerator) {
        this(config, ltmStore, archiveStore, embeddingGenerator, new HeuristicMemoryReconstructor());
    }

    public DeepRecallEngine(HypergraphMemoryConfig config,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            TextEmbeddingGenerator embeddingGenerator,
            MemoryReconstructor reconstructor) {
        this.config = config;
        this.ltmStore = ltmStore;
        this.archiveStore = archiveStore;
        this.embeddingGenerator = embeddingGenerator;
        this.reconstructor = reconstructor;
    }

    /**
     * 对指定查询执行深度回忆（使用配置的 deepRecallThreshold）。
     *
     * @param query 回忆查询文本
     * @return 重建后的记忆内容；未触发则返回 empty
     */
    public Optional<String> recall(String query) {
        return recall(query, config.deepRecallThreshold());
    }

    /**
     * 对指定查询执行深度回忆（自定义阈值，供主动注入使用）。
     * <p>完整回忆流程：语义检索 → 相似度计算 → 阈值判断 → 碎片还原 → 情境重建</p>
     *
     * @param query     回忆查询文本（如"昨天讨论的结论"）
     * @param threshold 相似度阈值，低于此值则不触发回忆
     * @return 重建后的记忆内容；如果未触发深度回忆（无候选/相似度不足/重建失败）则返回 empty
     */
    public Optional<String> recall(String query, double threshold) {
        List<HyperEdge> candidates = ltmStore.semanticSearch(config.memoryId(), query, config.vector().archiveSearchK());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Optional<ScoredEdge> bestOpt = candidates.stream()
                .map(edge -> new ScoredEdge(edge, similarity(query, edge)))
                .max(Comparator.comparingDouble(ScoredEdge::similarity));
        if (bestOpt.isEmpty()) {
            return Optional.empty();
        }
        ScoredEdge best = bestOpt.get();
        if (best.similarity() < threshold) {
            return Optional.empty();
        }
        List<String> fragments = archiveStore.retrieve(best.edge().archivePointers());
        String reconstructed = reconstructor.reconstruct(query, best.edge().compressedSummary(), fragments);
        return reconstructed == null || reconstructed.isBlank()
                ? Optional.empty()
                : Optional.of(reconstructed);
    }

    /**
     * 计算查询文本与超边的综合相似度。
     * <p>取 embedding 向量相似度和字面字符相似度的较大值，确保模型不可用时仍有兜底。</p>
     *
     * @param query 查询文本
     * @param edge  候选超边
     * @return 相似度 [0.0, 1.0]
     */
    private double similarity(String query, HyperEdge edge) {
        String queryText = query != null ? query : "";
        String docText = ((edge.compressedSummary() != null ? edge.compressedSummary() : "") + " "
                + (edge.messageText() != null ? edge.messageText() : ""));
        if (queryText.isBlank() || docText.isBlank()) {
            return 0;
        }
        try {
            Embedding queryEmbedding = embeddingGenerator.embed(queryText);
            Embedding docEmbedding = embeddingGenerator.embed(docText);
            double embeddingSimilarity = cosineSimilarity(queryEmbedding.vectorAsList(), docEmbedding.vectorAsList());
            return Math.max(embeddingSimilarity, lexicalSimilarity(queryText, docText));
        } catch (RuntimeException ex) {
            return lexicalSimilarity(queryText, docText);
        }
    }

    /**
     * 计算两个向量的余弦相似度。
     *
     * @param left  向量 A
     * @param right 向量 B
     * @return 相似度 [-1.0, 1.0]（实际使用中通常为 [0.0, 1.0]）
     */
    private double cosineSimilarity(List<Float> left, List<Float> right) {
        int size = Math.min(left.size(), right.size());
        if (size == 0) {
            return 0;
        }
        double dotProduct = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < size; i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dotProduct += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * 基于字符 bigram 的字面相似度（兜底策略）。
     * <p>使用相邻字符对（bigram）的 Jaccard 相似度，比字符集交集对中文语义更敏感：
     * "我叫什么名字" 和 "用户名字叫张三" 能通过 "名字" bigram 匹配，
     * 而字符集交集只能模糊匹配单个汉字。</p>
     * <p>同时保留单字符匹配作为补充（取两者较大值），防止超短文本退化。</p>
     *
     * @param queryText 查询文本
     * @param docText   文档文本
     * @return 相似度 [0.0, 1.0]
     */
    private double lexicalSimilarity(String queryText, String docText) {
        String q = queryText.replaceAll("\\s+", "");
        String d = docText.replaceAll("\\s+", "");
        if (q.isEmpty() || d.isEmpty()) {
            return 0;
        }
        // bigram Jaccard
        Set<String> qBigrams = bigrams(q);
        Set<String> dBigrams = bigrams(d);
        double bigramScore = 0;
        if (!qBigrams.isEmpty()) {
            long intersection = qBigrams.stream().filter(dBigrams::contains).count();
            long union = qBigrams.size() + dBigrams.size() - intersection;
            bigramScore = union == 0 ? 0 : intersection / (double) union;
        }
        // 单字符集交集（超短文本兜底）
        long matched = q.chars().distinct().filter(ch -> d.indexOf(ch) >= 0).count();
        long total = q.chars().distinct().count();
        double unigramScore = total == 0 ? 0 : matched / (double) total;
        return Math.max(bigramScore, unigramScore);
    }

    private Set<String> bigrams(String text) {
        if (text.length() < 2) {
            return text.isEmpty() ? Set.of() : Set.of(text);
        }
        Set<String> result = new HashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            result.add(text.substring(i, i + 2));
        }
        return result;
    }

    private record ScoredEdge(HyperEdge edge, double similarity) {
    }
}
