package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.config.DecayConfig;
import com.agentdsl.memory.hypergraph.model.HyperEdge;

import java.time.Duration;
import java.time.Instant;

public class DecayEngine {

    private final DecayConfig config;

    public DecayEngine(DecayConfig config) {
        this.config = config;
    }

    public double computeWeight(HyperEdge edge, Instant now) {
        if (edge.anchor()) {
            return 1.0;
        }

        double hours = Math.max(0.0, Duration.between(edge.lastAccessedAt(), now).toMinutes() / 60.0);
        double lambda = config.baseDecayRate() / (1.0 + edge.importance() * config.importanceBoost());
        double baseWeight = Math.max(0.01, edge.weight()) * Math.exp(-lambda * hours);
        double accessBoost = edge.accessCount() * config.accessBonus();
        return Math.min(1.0, baseWeight + accessBoost);
    }

    public boolean shouldCompress(HyperEdge edge, Instant now) {
        return !edge.anchor() && computeWeight(edge, now) < config.compressionThreshold();
    }

    public boolean shouldArchive(HyperEdge edge, Instant now) {
        return !edge.anchor() && computeWeight(edge, now) < config.archiveThreshold();
    }
}
