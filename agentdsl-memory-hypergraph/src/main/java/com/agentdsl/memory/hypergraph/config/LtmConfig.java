package com.agentdsl.memory.hypergraph.config;

public record LtmConfig(
        String backend,
        String path,
        String compressionModel,
        String jdbcUrl) {
}
