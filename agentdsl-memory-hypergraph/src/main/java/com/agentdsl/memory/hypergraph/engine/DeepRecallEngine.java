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
