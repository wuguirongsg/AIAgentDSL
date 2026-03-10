package com.agentdsl.runtime.autonomous;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行计划数据模型。
 * 由 PlannerEngine 生成，包含步骤列表和整体规划思路。
 */
public class ExecutionPlan {

    private String goal;
    private List<PlanStep> steps;
    private String reasoning;

    public ExecutionPlan() {
        this.steps = new ArrayList<>();
    }

    public ExecutionPlan(String goal, List<PlanStep> steps, String reasoning) {
        this.goal = goal;
        this.steps = steps != null ? steps : new ArrayList<>();
        this.reasoning = reasoning;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public List<PlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PlanStep> steps) {
        this.steps = steps;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    /**
     * 将计划格式化为用户可读的文本。
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 目标: ").append(goal).append("\n\n");
        if (reasoning != null && !reasoning.isBlank()) {
            sb.append("💡 规划思路: ").append(reasoning).append("\n\n");
        }
        sb.append("📋 执行计划:\n");
        for (int i = 0; i < steps.size(); i++) {
            PlanStep step = steps.get(i);
            sb.append(String.format("  %d. %s", i + 1, step.description()));
            if (step.toolOrSkill() != null && !step.toolOrSkill().isBlank()) {
                sb.append(String.format(" [使用: %s]", step.toolOrSkill()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 执行计划中的单个步骤。
     */
    public record PlanStep(
            int stepNumber,
            String description,
            String toolOrSkill,
            String expectedInput) {
    }
}
