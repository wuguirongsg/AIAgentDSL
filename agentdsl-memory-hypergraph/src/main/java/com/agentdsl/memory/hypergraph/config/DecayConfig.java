package com.agentdsl.memory.hypergraph.config;

public record DecayConfig(
        double baseDecayRate,
        double importanceBoost,
        double accessBonus,
        double compressionThreshold,
        double archiveThreshold) {

    public static DecayConfig defaults() {
        return new DecayConfig(0.08, 2.0, 0.05, 0.40, 0.20);
    }
}
