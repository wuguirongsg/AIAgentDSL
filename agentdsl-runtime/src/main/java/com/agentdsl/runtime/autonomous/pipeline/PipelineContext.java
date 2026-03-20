package com.agentdsl.runtime.autonomous.pipeline;

import com.agentdsl.runtime.autonomous.model.ExecutionStrategy;
import com.agentdsl.runtime.autonomous.model.ProblemSpec;

/**
 * Pipeline 执行上下文。
 * 贯穿各阶段的数据载体：由 Phase 1/2 写入，由 Phase 3/4 读取。
 */
public class PipelineContext {

    private ProblemSpec problemSpec;
    private ExecutionStrategy executionStrategy;

    // Phase 3/4 可写：用于动态切换策略
    private boolean strategyFallbackActivated = false;

    public PipelineContext() {}

    // ── Getters / Setters ─────────────────────────────────────────────

    public ProblemSpec getProblemSpec() { return problemSpec; }

    public void setProblemSpec(ProblemSpec problemSpec) {
        this.problemSpec = problemSpec;
    }

    public ExecutionStrategy getExecutionStrategy() { return executionStrategy; }

    public void setExecutionStrategy(ExecutionStrategy executionStrategy) {
        this.executionStrategy = executionStrategy;
    }

    public boolean isStrategyFallbackActivated() { return strategyFallbackActivated; }

    public void activateFallbackStrategy() { this.strategyFallbackActivated = true; }

    // ── 便利查询方法 ──────────────────────────────────────────────────

    /**
     * 成功标准列表（注入 ReAct System Prompt）。
     */
    public java.util.List<String> getSuccessCriteria() {
        return problemSpec != null ? problemSpec.getSuccessCriteria() : java.util.List.of();
    }

    /**
     * 当前活跃策略步骤文本（注入 ReAct System Prompt）。
     */
    public String getActiveStrategyStepsText() {
        if (executionStrategy == null) return "";
        return executionStrategy.formatPrimaryStepsAsText();
    }

    /**
     * 估算步骤数（可作为 max_steps 的参考值）。
     */
    public int getEstimatedSteps() {
        return problemSpec != null ? problemSpec.getEstimatedSteps() : 0;
    }

    @Override
    public String toString() {
        return "PipelineContext{problem=" + problemSpec
                + ", strategy=" + executionStrategy
                + ", fallbackActivated=" + strategyFallbackActivated + "}";
    }
}
