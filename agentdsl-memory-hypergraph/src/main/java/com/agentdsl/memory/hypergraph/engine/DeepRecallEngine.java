package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.embedding.EmbeddingGeneratorFactory;
import com.agentdsl.memory.hypergraph.embedding.TextEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.store.LtmStore;
import com.agentdsl.memory.hypergraph.store.VectorArchiveStore;
import dev.langchain4j.data.embedding.Embedding;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
     * 对指定查询执行深度回忆。
     * <p>完整回忆流程：语义检索 → 相似度计算 → 阈值判断 → 碎片还原 → 情境重建</p>
     *
     * @param query 回忆查询文本（如"昨天讨论的结论"）
     * @return 重建后的记忆内容；如果未触发深度回忆（无候选/相似度不足/重建失败）则返回 empty
     */
    public Optional<String> recall(String query) {
        List<HyperEdge> candidates = ltmStore.semanticSearch(config.memoryId(), query, config.vector().archiveSearchK());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        ScoredEdge best = candidates.stream()
                .map(edge -> new ScoredEdge(edge, similarity(query, edge)))
                .max(Comparator.comparingDouble(ScoredEdge::similarity))
                .orElse(null);
        if (best == null) {
            return Optional.empty();
        }
        double similarity = best.similarity();
        if (similarity < config.deepRecallThreshold()) {
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
     * 基于字符集交集的字面相似度（兜底策略）。
     * <p>当 embedding 模型不可用时，使用字符级匹配估算相似度。</p>
     *
     * @param queryText 查询文本
     * @param docText   文档文本
     * @return 相似度 [0.0, 1.0]
     */
    private double lexicalSimilarity(String queryText, String docText) {
        long matched = queryText.chars()
                .filter(ch -> !Character.isWhitespace(ch))
                .distinct()
                .filter(ch -> docText.indexOf(ch) >= 0)
                .count();
        long total = queryText.chars()
                .filter(ch -> !Character.isWhitespace(ch))
                .distinct()
                .count();
        return total == 0 ? 0 : matched / (double) total;
    }

    private record ScoredEdge(HyperEdge edge, double similarity) {
    }
}
