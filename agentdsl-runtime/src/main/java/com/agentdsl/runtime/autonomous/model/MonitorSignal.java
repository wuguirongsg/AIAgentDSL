package com.agentdsl.runtime.autonomous.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 4（元认知监控）输出信号。
 * 由 ExecutionMonitor 在每步执行后产生，告知 ReAct 循环是否需要干预。
 */
public class MonitorSignal {

    /** 干预类型 */
    public enum InterventionType {
        /** 切换到备用策略 */
        STRATEGY_SWITCH,
        /** 回退到 Phase 2 重新规划 */
        REPLAN,
        /** 压缩消息历史（Token 超限）*/
        COMPRESS_CONTEXT,
        /** 强制 LLM 给出当前最佳答案（时间/Token 耗尽）*/
        FORCE_CONCLUDE,
        /** 解决前后矛盾 */
        CONTRADICTION_RESOLUTION
    }

    /** 严重程度 */
    public enum Severity {
        LOW, MEDIUM, HIGH;

        public int level() {
            return switch (this) {
                case LOW -> 1;
                case MEDIUM -> 2;
                case HIGH -> 3;
            };
        }
    }

    /** 单条干预指令 */
    public record Intervention(
            InterventionType type,
            Severity severity,
            String message   // 会被注入到下一轮 LLM Prompt
    ) {}

    // ── 实例字段 ─────────────────────────────────────────────────────

    private final boolean healthy;
    private final List<Intervention> interventions;

    private MonitorSignal(boolean healthy, List<Intervention> interventions) {
        this.healthy = healthy;
        this.interventions = Collections.unmodifiableList(interventions);
    }

    // ── 工厂方法 ─────────────────────────────────────────────────────

    public static MonitorSignal healthy() {
        return new MonitorSignal(true, List.of());
    }

    public static MonitorSignal intervene(List<Intervention> interventions) {
        return new MonitorSignal(false, interventions);
    }

    // ── 查询方法 ─────────────────────────────────────────────────────

    public boolean isHealthy() { return healthy; }

    public boolean requiresIntervention() { return !healthy && !interventions.isEmpty(); }

    public List<Intervention> getInterventions() { return interventions; }

    public boolean hasType(InterventionType type) {
        return interventions.stream().anyMatch(i -> i.type() == type);
    }

    public Severity highestSeverity() {
        return interventions.stream()
                .map(Intervention::severity)
                .max(Comparator.comparingInt(Severity::level))
                .orElse(Severity.LOW);
    }

    /**
     * 生成注入 LLM 下一轮 Prompt 的干预消息文本。
     * 按严重程度排序，取最重要的前 2 条。
     */
    public String buildInjectionMessage() {
        return interventions.stream()
                .sorted(Comparator.comparingInt((Intervention i) -> i.severity().level()).reversed())
                .limit(2)
                .map(i -> "[元认知监控-" + i.severity() + "] " + i.message())
                .collect(Collectors.joining("\n\n"));
    }

    @Override
    public String toString() {
        if (healthy) return "MonitorSignal{HEALTHY}";
        return "MonitorSignal{interventions=" + interventions.size()
                + ", highestSeverity=" + highestSeverity() + "}";
    }
}
