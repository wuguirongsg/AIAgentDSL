package com.agentdsl.memory.hypergraph.config;

public record VectorConfig(
        String store,
        String embeddingModel,
        String path,
        int archiveEmbeddingDimension,
        int archiveSearchK) {
}
