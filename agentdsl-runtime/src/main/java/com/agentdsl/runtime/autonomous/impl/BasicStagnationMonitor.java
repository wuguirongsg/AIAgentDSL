package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.MonitorSignal;
import com.agentdsl.runtime.autonomous.model.StepContext;
import com.agentdsl.runtime.autonomous.pipeline.ExecutionMonitor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 基础停滞监控器（轻量 Phase 4 实现）。
 * 仅检测停滞（连续相同操作），零 LLM 成本。
 * 适用于 {@code plan} 和 {@code fast} preset。
 *
 * <p>复用现有 {@code AutonomousExecutor.StagnationDetector} 的逻辑，
 * 升级为实现 {@link ExecutionMonitor} 接口，纳入 Pipeline 体系。
 */
public class BasicStagnationMonitor implements ExecutionMonitor {

    private static final int STAGNATION_WINDOW = 3;

    private final Deque<String> recentFingerprints = new ArrayDeque<>();

    @Override
    public MonitorSignal analyze(StepContext ctx) {
        String fingerprint = ctx.actionFingerprint();

        // 无工具调用时不计入停滞判断
        if ("NO_TOOL".equals(fingerprint)) {
            return MonitorSignal.healthy();
        }

        recentFingerprints.addLast(fingerprint);
        if (recentFingerprints.size() > STAGNATION_WINDOW) {
            recentFingerprints.removeFirst();
        }

        boolean isStagnant = recentFingerprints.size() == STAGNATION_WINDOW
                && recentFingerprints.stream().distinct().count() == 1;

        if (isStagnant) {
            recentFingerprints.clear(); // 触发后重置，避免连续触发
            return MonitorSignal.intervene(List.of(new MonitorSignal.Intervention(
                    MonitorSignal.InterventionType.STRATEGY_SWITCH,
                    MonitorSignal.Severity.HIGH,
                    "检测到执行停滞（连续 " + STAGNATION_WINDOW + " 步相同操作）。\n" +
                    "请重新分析问题，考虑：\n" +
                    "1. 当前路径是否可行？\n" +
                    "2. 是否需要换一种工具或方法？\n" +
                    "3. 是否需要拆分任务为更小的子目标？"
            )));
        }

        return MonitorSignal.healthy();
    }

    @Override
    public void reset() {
        recentFingerprints.clear();
    }
}
