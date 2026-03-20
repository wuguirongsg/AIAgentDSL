package com.agentdsl.runtime.autonomous.model;

import java.util.Collections;
import java.util.List;

/**
 * Phase 2（策略规划）候选策略。
 * 一个候选策略代表一种不同思路的执行路径。
 */
public class CandidateStrategy {

    /** 策略中的单个步骤 */
    public record Step(
            String action,
            String tool,      // 可为 null（不需要工具的推理步骤）
            String risk       // "low" | "medium" | "high"
    ) {}

    private final String id;
    private final String name;
    private final String approach;   // 核心思路描述
    private final String tradeoffs;  // 优缺点分析
    private final List<Step> steps;
    private final int estimatedSteps;
    private final double confidence; // LLM 自评置信度 0.0-1.0

    private CandidateStrategy(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.approach = builder.approach;
        this.tradeoffs = builder.tradeoffs;
        this.steps = Collections.unmodifiableList(builder.steps);
        this.estimatedSteps = builder.estimatedSteps > 0
                ? builder.estimatedSteps : builder.steps.size();
        this.confidence = Math.max(0.0, Math.min(1.0, builder.confidence));
    }

    // ── Getters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public String getApproach() { return approach; }
    public String getTradeoffs() { return tradeoffs; }
    public List<Step> getSteps() { return steps; }
    public int getEstimatedSteps() { return estimatedSteps; }
    public double getConfidence() { return confidence; }

    // ── Builder ───────────────────────────────────────────────────────

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public static class Builder {
        private final String id;
        private final String name;
        private String approach = "";
        private String tradeoffs = "";
        private List<Step> steps = List.of();
        private int estimatedSteps = 0;
        private double confidence = 0.5;

        public Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder approach(String v) { approach = v; return this; }
        public Builder tradeoffs(String v) { tradeoffs = v; return this; }
        public Builder steps(List<Step> v) { steps = v; return this; }
        public Builder estimatedSteps(int v) { estimatedSteps = v; return this; }
        public Builder confidence(double v) { confidence = v; return this; }

        public CandidateStrategy build() { return new CandidateStrategy(this); }
    }

    @Override
    public String toString() {
        return "CandidateStrategy{id='" + id + "', name='" + name
                + "', steps=" + steps.size() + ", confidence=" + confidence + "}";
    }
}
