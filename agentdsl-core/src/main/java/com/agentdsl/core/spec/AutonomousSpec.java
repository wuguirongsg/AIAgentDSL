package com.agentdsl.core.spec;

/**
 * 自主 Agent 配置规范模型。
 * 对应 DSL 中 agent 内的 autonomous { ... } 块。
 *
 * <pre>
 * autonomous {
 *     execution_mode "plan"   // "plan" | "fast"
 *     max_steps 10
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
                '}';
    }
}
