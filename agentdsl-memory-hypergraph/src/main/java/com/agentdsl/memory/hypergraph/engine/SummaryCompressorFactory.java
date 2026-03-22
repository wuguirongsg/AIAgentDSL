package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.config.LtmConfig;

public final class SummaryCompressorFactory {

    private SummaryCompressorFactory() {
    }

    public static SummaryCompressor create(LtmConfig ltmConfig) {
        if (ltmConfig != null && ltmConfig.compressionModel() != null && !ltmConfig.compressionModel().isBlank()) {
            return new ExtractiveSummaryCompressor();
        }
        return new HeuristicSummaryCompressor();
    }
}
