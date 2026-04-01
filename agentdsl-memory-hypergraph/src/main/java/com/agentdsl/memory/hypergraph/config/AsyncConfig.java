package com.agentdsl.memory.hypergraph.config;

/**
 * 异步层配置（v1.2 新增）。
 *
 * <p>控制 IngestQueue、ScoringBatchWorker、SummaryPrecomputer 的行为参数。</p>
 */
public record AsyncConfig(
        int ingestQueueCapacity,
        int scoringBatchSize,
        long scoringIntervalMinutes,
        long precomputeIntervalMinutes,
        int neighborHops) {

    public static AsyncConfig defaults() {
        return new AsyncConfig(1000, 20, 5, 30, 2);
    }
}
