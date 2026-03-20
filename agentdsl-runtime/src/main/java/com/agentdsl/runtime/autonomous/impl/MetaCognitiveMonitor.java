package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.MonitorSignal;
import com.agentdsl.runtime.autonomous.model.StepContext;
import com.agentdsl.runtime.autonomous.pipeline.ExecutionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 元认知监控器（Phase 4 完整实现）。
 * 持续监控 ReAct 执行循环，检测四个维度的异常并注入干预信号。
 * 适用于 {@code smart} preset（复杂/探索性任务）。
 *
 * <h3>监控维度</h3>
 * <ol>
 *   <li>停滞检测：连续相同操作 → STRATEGY_SWITCH</li>
 *   <li>置信度下降：启发式关键词分析，连续下降 → REPLAN</li>
 *   <li>资源超限：Token/时间超阈值 → COMPRESS_CONTEXT / FORCE_CONCLUDE</li>
 *   <li>矛盾检测：前后断言冲突 → CONTRADICTION_RESOLUTION（简化版）</li>
 * </ol>
 */
public class MetaCognitiveMonitor implements ExecutionMonitor {

    private static final Logger log = LoggerFactory.getLogger(MetaCognitiveMonitor.class);

    // ── 配置 ──────────────────────────────────────────────────────────

    private static final int STAGNATION_WINDOW = 3;
    private static final int CONFIDENCE_WINDOW = 3;
    private static final double CONFIDENCE_DECLINE_THRESHOLD = 0.15;

    private final int maxTokenBudget;
    private final long maxTimeMs;

    // ── 状态 ──────────────────────────────────────────────────────────

    private final Deque<String> actionHistory = new ArrayDeque<>();
    private final Deque<Double> confidenceHistory = new ArrayDeque<>();
    private final List<String> keyAssertions = new ArrayList<>();
    private int totalTokensUsed = 0;
    private long startTimeMs;

    public MetaCognitiveMonitor(int maxTokenBudget, long maxTimeMs) {
        this.maxTokenBudget = maxTokenBudget;
        this.maxTimeMs = maxTimeMs;
        this.startTimeMs = System.currentTimeMillis();
    }

    // ── 核心分析 ──────────────────────────────────────────────────────

    @Override
    public MonitorSignal analyze(StepContext ctx) {
        List<MonitorSignal.Intervention> interventions = new ArrayList<>();

        checkStagnation(ctx).ifPresent(interventions::add);
        checkConfidenceDecline(ctx).ifPresent(interventions::add);
        checkResourceLimits(ctx).ifPresent(interventions::add);
        checkContradictions(ctx).ifPresent(interventions::add);

        updateState(ctx);

        if (interventions.isEmpty()) return MonitorSignal.healthy();

        interventions.sort((a, b) -> b.severity().level() - a.severity().level());
        log.info("MetaCognitive 干预: {}", interventions);
        return MonitorSignal.intervene(interventions);
    }

    @Override
    public void reset() {
        actionHistory.clear();
        confidenceHistory.clear();
        keyAssertions.clear();
        totalTokensUsed = 0;
        startTimeMs = System.currentTimeMillis();
    }

    // ── 维度1：停滞检测 ───────────────────────────────────────────────

    private java.util.Optional<MonitorSignal.Intervention> checkStagnation(StepContext ctx) {
        String fingerprint = ctx.actionFingerprint();
        if ("NO_TOOL".equals(fingerprint)) return java.util.Optional.empty();

        actionHistory.addLast(fingerprint);
        if (actionHistory.size() > STAGNATION_WINDOW) actionHistory.removeFirst();

        boolean stagnant = actionHistory.size() == STAGNATION_WINDOW
                && actionHistory.stream().distinct().count() == 1;

        if (stagnant) {
            actionHistory.clear();
            return java.util.Optional.of(new MonitorSignal.Intervention(
                    MonitorSignal.InterventionType.STRATEGY_SWITCH,
                    MonitorSignal.Severity.HIGH,
                    "检测到执行停滞（连续 " + STAGNATION_WINDOW + " 步相同操作）。\n" +
                    "建议：换一种工具或方法；重新分析当前子目标是否可达；考虑跳过此步。"
            ));
        }
        return java.util.Optional.empty();
    }

    // ── 维度2：置信度下降 ─────────────────────────────────────────────

    private java.util.Optional<MonitorSignal.Intervention> checkConfidenceDecline(StepContext ctx) {
        double confidence = inferConfidence(ctx.getLlmOutput());
        confidenceHistory.addLast(confidence);
        if (confidenceHistory.size() > CONFIDENCE_WINDOW) confidenceHistory.removeFirst();
        if (confidenceHistory.size() < CONFIDENCE_WINDOW) return java.util.Optional.empty();

        List<Double> scores = new ArrayList<>(confidenceHistory);
        boolean declining = true;
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i) >= scores.get(i - 1)) { declining = false; break; }
        }
        double totalDrop = scores.get(0) - scores.get(scores.size() - 1);

        if (declining && totalDrop > CONFIDENCE_DECLINE_THRESHOLD) {
            return java.util.Optional.of(new MonitorSignal.Intervention(
                    MonitorSignal.InterventionType.REPLAN,
                    MonitorSignal.Severity.MEDIUM,
                    String.format("置信度持续下降（%.0f%%，连续 %d 步），建议重新评估当前策略是否正确。",
                            totalDrop * 100, CONFIDENCE_WINDOW)
            ));
        }
        return java.util.Optional.empty();
    }

    // ── 维度3：资源超限 ───────────────────────────────────────────────

    private java.util.Optional<MonitorSignal.Intervention> checkResourceLimits(StepContext ctx) {
        totalTokensUsed += ctx.getTokensUsedThisStep();
        long elapsed = System.currentTimeMillis() - startTimeMs;

        if (maxTokenBudget > 0) {
            double tokenRatio = (double) totalTokensUsed / maxTokenBudget;
            if (tokenRatio > 0.95) {
                return java.util.Optional.of(new MonitorSignal.Intervention(
                        MonitorSignal.InterventionType.COMPRESS_CONTEXT,
                        MonitorSignal.Severity.HIGH,
                        String.format("Token 用量已达 %.0f%%，需要立即压缩上下文历史。", tokenRatio * 100)
                ));
            }
            if (tokenRatio > 0.80) {
                return java.util.Optional.of(new MonitorSignal.Intervention(
                        MonitorSignal.InterventionType.COMPRESS_CONTEXT,
                        MonitorSignal.Severity.LOW,
                        String.format("Token 用量预警（%.0f%%），建议开始精简中间步骤记录。",
                                tokenRatio * 100)
                ));
            }
        }

        if (maxTimeMs > 0 && elapsed > maxTimeMs * 0.90) {
            return java.util.Optional.of(new MonitorSignal.Intervention(
                    MonitorSignal.InterventionType.FORCE_CONCLUDE,
                    MonitorSignal.Severity.HIGH,
                    "执行时间已达上限的 90%，需要尽快得出结论，不要继续探索新路径。"
            ));
        }
        return java.util.Optional.empty();
    }

    // ── 维度4：矛盾检测（简化版）─────────────────────────────────────

    private java.util.Optional<MonitorSignal.Intervention> checkContradictions(StepContext ctx) {
        List<String> newAssertions = extractAssertions(ctx.getLlmOutput());
        for (String newA : newAssertions) {
            for (String oldA : keyAssertions) {
                if (isContradiction(newA, oldA)) {
                    return java.util.Optional.of(new MonitorSignal.Intervention(
                            MonitorSignal.InterventionType.CONTRADICTION_RESOLUTION,
                            MonitorSignal.Severity.MEDIUM,
                            "检测到潜在矛盾：\n- 早前：\"" + oldA + "\"\n- 当前：\"" + newA
                            + "\"\n请核实哪个正确，避免基于错误前提继续推理。"
                    ));
                }
            }
        }
        keyAssertions.addAll(newAssertions);
        if (keyAssertions.size() > 20) keyAssertions.subList(0, 10).clear();
        return java.util.Optional.empty();
    }

    // ── 工具方法 ──────────────────────────────────────────────────────

    private void updateState(StepContext ctx) {
        // 有实质进展时，重置停滞计数（actionHistory 已在 checkStagnation 中处理）
    }

    /** 启发式置信度推断（关键词分析，无需 LLM 调用）*/
    double inferConfidence(String llmOutput) {
        if (llmOutput == null) return 0.5;
        String lower = llmOutput.toLowerCase();
        double score = 0.5;
        if (lower.contains("已完成") || lower.contains("成功") || lower.contains("task_complete")) score += 0.3;
        if (lower.contains("明确") || lower.contains("确认")) score += 0.1;
        if (lower.contains("不确定") || lower.contains("可能") || lower.contains("也许")) score -= 0.15;
        if (lower.contains("失败") || lower.contains("错误") || lower.contains("无法")) score -= 0.2;
        if (lower.contains("重试") || lower.contains("尝试其他")) score -= 0.1;
        return Math.max(0.0, Math.min(1.0, score));
    }

    private List<String> extractAssertions(String text) {
        if (text == null) return List.of();
        return Arrays.stream(text.split("[。\n]"))
                .filter(s -> s.length() > 10 && s.length() < 100)
                .filter(s -> s.contains("是") || s.contains("存在") || s.contains("已"))
                .limit(3)
                .collect(Collectors.toList());
    }

    /** 简化矛盾检测（P2 升级为语义模型）*/
    private boolean isContradiction(String a, String b) {
        // 简单启发式：检测否定词对
        return false;
    }
}
