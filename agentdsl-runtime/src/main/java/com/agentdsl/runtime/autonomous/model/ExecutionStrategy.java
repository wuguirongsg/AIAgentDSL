package com.agentdsl.runtime.autonomous.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Phase 2（策略规划）最终输出。
 * 包含主执行路径和备用路径（主路径失败/停滞时切换）。
 */
public class ExecutionStrategy {

    /** 带评分的策略封装 */
    public record ScoredStrategy(
            CandidateStrategy strategy,
            double score,
            java.util.Map<String, Double> breakdown  // 各维度得分明细
    ) {}

    private final ScoredStrategy primary;
    private final ScoredStrategy fallback;      // 可为 null（备用策略）
    private final List<ScoredStrategy> allCandidates;

    public ExecutionStrategy(ScoredStrategy primary,
                              ScoredStrategy fallback,
                              List<ScoredStrategy> allCandidates) {
        this.primary = primary;
        this.fallback = fallback;
        this.allCandidates = allCandidates == null
                ? List.of() : Collections.unmodifiableList(allCandidates);
    }

    /**
     * Phase 3 停滞时，从备用策略切换。
     */
    public Optional<CandidateStrategy> getFallbackStrategy() {
        return Optional.ofNullable(fallback).map(ScoredStrategy::strategy);
    }

    public ScoredStrategy getPrimary() { return primary; }
    public ScoredStrategy getFallback() { return fallback; }
    public List<ScoredStrategy> getAllCandidates() { return allCandidates; }

    /**
     * 获取主策略核心思路，用于注入 ReAct System Prompt。
     */
    public String getPrimaryApproach() {
        return primary == null ? "" : primary.strategy().getApproach();
    }

    /**
     * 将主策略步骤格式化为文字，注入 ReAct System Prompt。
     */
    public String formatPrimaryStepsAsText() {
        if (primary == null || primary.strategy().getSteps().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (CandidateStrategy.Step step : primary.strategy().getSteps()) {
            sb.append(idx++).append(". ").append(step.action());
            if (step.tool() != null) sb.append("（工具: ").append(step.tool()).append("）");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 无规划时的空策略（fast 模式或规划失败降级场景）。
     */
    public static ExecutionStrategy empty() {
        CandidateStrategy emptyCandidateStrategy = CandidateStrategy.builder("empty", "直接执行")
                .approach("直接基于目标进行 ReAct 推理，不使用预定策略")
                .confidence(0.5)
                .steps(List.of())
                .build();
        ScoredStrategy scored = new ScoredStrategy(emptyCandidateStrategy, 0.5, java.util.Map.of());
        return new ExecutionStrategy(scored, null, List.of(scored));
    }

    @Override
    public String toString() {
        String primaryName = primary != null ? primary.strategy().getName() : "none";
        String fallbackName = fallback != null ? fallback.strategy().getName() : "none";
        return "ExecutionStrategy{primary='" + primaryName + "', fallback='" + fallbackName + "'}";
    }
}
