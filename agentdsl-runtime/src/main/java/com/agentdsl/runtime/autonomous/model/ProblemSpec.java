package com.agentdsl.runtime.autonomous.model;

import java.util.Collections;
import java.util.List;

/**
 * Phase 1（问题解构）输出结果。
 * 描述用户目标的结构化分析，为后续阶段提供依据。
 */
public class ProblemSpec {

    /** 任务类型 */
    public enum TaskType {
        /** 单步骤，直接 ReAct */
        SINGLE_STEP,
        /** 多步骤，需规划 */
        MULTI_STEP,
        /** 探索性，路径不确定 */
        EXPLORATORY,
        /** 开放性，边界模糊 */
        OPEN_ENDED
    }

    /** 复杂度 */
    public enum ComplexityLevel {
        SIMPLE, MEDIUM, COMPLEX
    }

    /** 子目标（含依赖关系） */
    public record SubGoal(String id, String goal, List<String> dependsOn) {
        public SubGoal {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }

    private final String originalGoal;
    private final TaskType taskType;
    private final ComplexityLevel complexity;
    private final List<String> constraints;
    private final List<String> successCriteria;
    private final List<String> uncertainties;
    private final List<String> requiredTools;
    private final List<String> missingCapabilities;
    private final int estimatedSteps;
    private final List<SubGoal> decomposedSubGoals;

    private ProblemSpec(Builder builder) {
        this.originalGoal = builder.originalGoal;
        this.taskType = builder.taskType;
        this.complexity = builder.complexity;
        this.constraints = Collections.unmodifiableList(builder.constraints);
        this.successCriteria = Collections.unmodifiableList(builder.successCriteria);
        this.uncertainties = Collections.unmodifiableList(builder.uncertainties);
        this.requiredTools = Collections.unmodifiableList(builder.requiredTools);
        this.missingCapabilities = Collections.unmodifiableList(builder.missingCapabilities);
        this.estimatedSteps = builder.estimatedSteps;
        this.decomposedSubGoals = Collections.unmodifiableList(builder.decomposedSubGoals);
    }

    /**
     * 当前工具集是否满足任务所需能力。
     */
    public boolean isExecutable() {
        return missingCapabilities.isEmpty();
    }

    /**
     * 根据任务类型推荐执行策略 preset。
     */
    public String recommendPreset() {
        return switch (taskType) {
            case SINGLE_STEP -> "fast";
            case MULTI_STEP -> "plan";
            case EXPLORATORY, OPEN_ENDED -> "smart";
        };
    }

    // ── Getters ──────────────────────────────────────────────────────

    public String getOriginalGoal() { return originalGoal; }
    public TaskType getTaskType() { return taskType; }
    public ComplexityLevel getComplexity() { return complexity; }
    public List<String> getConstraints() { return constraints; }
    public List<String> getSuccessCriteria() { return successCriteria; }
    public List<String> getUncertainties() { return uncertainties; }
    public List<String> getRequiredTools() { return requiredTools; }
    public List<String> getMissingCapabilities() { return missingCapabilities; }
    public int getEstimatedSteps() { return estimatedSteps; }
    public List<SubGoal> getDecomposedSubGoals() { return decomposedSubGoals; }

    // ── 工厂方法 ─────────────────────────────────────────────────────

    /**
     * 降级用：解构失败时返回最简规格，保证流程不中断。
     */
    public static ProblemSpec defaultSpec(String originalGoal) {
        return new Builder(originalGoal)
                .taskType(TaskType.MULTI_STEP)
                .complexity(ComplexityLevel.MEDIUM)
                .successCriteria(List.of("用户目标得到满足"))
                .estimatedSteps(5)
                .build();
    }

    public static Builder builder(String originalGoal) {
        return new Builder(originalGoal);
    }

    // ── Builder ───────────────────────────────────────────────────────

    public static class Builder {
        private final String originalGoal;
        private TaskType taskType = TaskType.MULTI_STEP;
        private ComplexityLevel complexity = ComplexityLevel.MEDIUM;
        private List<String> constraints = List.of();
        private List<String> successCriteria = List.of();
        private List<String> uncertainties = List.of();
        private List<String> requiredTools = List.of();
        private List<String> missingCapabilities = List.of();
        private int estimatedSteps = 5;
        private List<SubGoal> decomposedSubGoals = List.of();

        public Builder(String originalGoal) {
            this.originalGoal = originalGoal;
        }

        public Builder taskType(TaskType v) { taskType = v; return this; }
        public Builder complexity(ComplexityLevel v) { complexity = v; return this; }
        public Builder constraints(List<String> v) { constraints = v; return this; }
        public Builder successCriteria(List<String> v) { successCriteria = v; return this; }
        public Builder uncertainties(List<String> v) { uncertainties = v; return this; }
        public Builder requiredTools(List<String> v) { requiredTools = v; return this; }
        public Builder missingCapabilities(List<String> v) { missingCapabilities = v; return this; }
        public Builder estimatedSteps(int v) { estimatedSteps = v; return this; }
        public Builder decomposedSubGoals(List<SubGoal> v) { decomposedSubGoals = v; return this; }

        public ProblemSpec build() { return new ProblemSpec(this); }
    }

    @Override
    public String toString() {
        return "ProblemSpec{type=" + taskType + ", complexity=" + complexity
                + ", estimatedSteps=" + estimatedSteps + ", criteria=" + successCriteria + "}";
    }
}
