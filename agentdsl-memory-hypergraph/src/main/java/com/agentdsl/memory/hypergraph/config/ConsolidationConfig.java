package com.agentdsl.memory.hypergraph.config;

public record ConsolidationConfig(
        int intervalHours,
        boolean autoStart) {
}
