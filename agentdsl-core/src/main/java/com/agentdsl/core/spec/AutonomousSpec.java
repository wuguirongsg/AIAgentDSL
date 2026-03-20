package com.agentdsl.core.spec;

/**
 * 自主 Agent 配置规范模型。
 * 对应 DSL 中 agent 内的 autonomous { ... } 块。
 *
 * <pre>
 * autonomous {
 *     execution_mode "plan"    // "plan" | "fast"
 *     max_steps 10
 *     preset "smart"           // "smart" | "plan" | "fast"
 *     max_token_budget 80000   // 元认知监控 Token 预算
 *     max_time_ms 300000       // 元认知监控时间预算（毫秒）
 * }
 * </pre>
 */
public class AutonomousSpec {

    /**
     * 执行模式：
     * - "plan": 先生成执行计划，用户确认后再执行
     * - "fast": 直接开始 ReAct 循环执行
     */
    private String executionMode = "plan";

    /**
     * 最大执行步骤数。
     * 超过此阈值后暂停并询问用户是否继续。
     */
    private int maxSteps = 10;

    /**
     * Pipeline 预设：决定使用哪组实现组合。
     * - "smart": LlmDecomposer + TotPlanner + MetaCognitiveMonitor（复杂任务）
     * - "plan":  LlmDecomposer + LinearPlanner + BasicStagnationMonitor（默认）
     * - "fast":  DefaultDecomposer + LinearPlanner + BasicStagnationMonitor（简单任务）
     */
    private String preset = "plan";

    /**
     * 元认知监控的 Token 预算（0 表示不限制）。
     * 超过 80% 时预警，95% 时强制压缩上下文。
     */
    private int maxTokenBudget = 80_000;

    /**
     * 元认知监控的时间预算（毫秒，0 表示不限制）。
     * 超过 90% 时触发 FORCE_CONCLUDE 干预。
     */
    private long maxTimeMs = 0;

    public AutonomousSpec() {
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public int getMaxTokenBudget() {
        return maxTokenBudget;
    }

    public void setMaxTokenBudget(int maxTokenBudget) {
        this.maxTokenBudget = maxTokenBudget;
    }

    public long getMaxTimeMs() {
        return maxTimeMs;
    }

    public void setMaxTimeMs(long maxTimeMs) {
        this.maxTimeMs = maxTimeMs;
    }

    public boolean isPlanMode() {
        return "plan".equalsIgnoreCase(executionMode);
    }

    public boolean isFastMode() {
        return "fast".equalsIgnoreCase(executionMode);
    }

    @Override
    public String toString() {
        return "AutonomousSpec{" +
                "executionMode='" + executionMode + '\'' +
                ", maxSteps=" + maxSteps +
                ", preset='" + preset + '\'' +
                ", maxTokenBudget=" + maxTokenBudget +
                '}';
    }
}
