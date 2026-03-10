package com.agentdsl.runtime.autonomous;

import java.util.ArrayList;
import java.util.List;

/**
 * 自主执行结果。
 */
public class AutonomousResult {

    private String finalAnswer;
    private ExecutionPlan plan;
    private List<StepResult> executedSteps;
    private int totalSteps;
    private boolean completed;
    private String terminationReason;

    public AutonomousResult() {
        this.executedSteps = new ArrayList<>();
    }

    // --- Getters & Setters ---

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public ExecutionPlan getPlan() {
        return plan;
    }

    public void setPlan(ExecutionPlan plan) {
        this.plan = plan;
    }

    public List<StepResult> getExecutedSteps() {
        return executedSteps;
    }

    public void setExecutedSteps(List<StepResult> executedSteps) {
        this.executedSteps = executedSteps;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    /**
     * 每个步骤的执行详情。
     */
    public record StepResult(
            int stepNumber,
            String action,
            String observation,
            long durationMs) {
    }
}
