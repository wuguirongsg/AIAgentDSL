package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.CandidateStrategy;
import com.agentdsl.runtime.autonomous.model.ExecutionStrategy;
import com.agentdsl.runtime.autonomous.model.MonitorSignal;
import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import com.agentdsl.runtime.autonomous.model.StepContext;
import com.agentdsl.runtime.autonomous.pipeline.PipelineThoughtListener;

import java.util.stream.Collectors;

/**
 * 控制台输出的 Pipeline 思考监听器。
 * 以树状结构展示 Agent 的内部思考过程，便于用户理解 Agent 的决策逻辑。
 *
 * <p>输出示例：
 * <pre>
 * 🧠 Phase 1: 问题解构
 *   ├─ 任务类型: multi_step
 *   ├─ 复杂度: complex
 *   └─ 子目标: [...]
 *
 * 🌳 Phase 2: 策略规划
 *   ├─ 生成 3 条候选策略
 *   └─ 主路径步骤: [...]
 *
 * 📊 Phase 4: 监控 (步骤 N)
 *   └─ [干预信号]
 * </pre>
 */
public class ConsolePipelineThoughtListener implements PipelineThoughtListener {

    @Override
    public void onDecomposition(ProblemSpec problem) {
        System.out.println();
        System.out.println("🧠 Phase 1: 问题解构");
        System.out.println("  ├─ 任务类型: " + formatTaskType(problem.getTaskType()));
        System.out.println("  ├─ 复杂度: " + formatComplexity(problem.getComplexity()));
        System.out.println("  ├─ 预估步数: " + problem.getEstimatedSteps());

        if (!problem.getConstraints().isEmpty()) {
            System.out.println("  ├─ 约束:");
            for (int i = 0; i < problem.getConstraints().size(); i++) {
                String prefix = i == problem.getConstraints().size() - 1 ? "  │   └─" : "  │   ├─";
                System.out.println(prefix + " " + problem.getConstraints().get(i));
            }
        }

        if (!problem.getSuccessCriteria().isEmpty()) {
            System.out.println("  ├─ 成功标准:");
            for (int i = 0; i < problem.getSuccessCriteria().size(); i++) {
                String prefix = i == problem.getSuccessCriteria().size() - 1 ? "  │   └─" : "  │   ├─";
                System.out.println(prefix + " " + problem.getSuccessCriteria().get(i));
            }
        }

        if (!problem.getUncertainties().isEmpty()) {
            System.out.println("  ├─ 不确定因素:");
            for (int i = 0; i < problem.getUncertainties().size(); i++) {
                String prefix = i == problem.getUncertainties().size() - 1 ? "  │   └─" : "  │   ├─";
                System.out.println(prefix + " " + problem.getUncertainties().get(i));
            }
        }

        if (!problem.getDecomposedSubGoals().isEmpty()) {
            System.out.println("  └─ 子目标:");
            for (int i = 0; i < problem.getDecomposedSubGoals().size(); i++) {
                var sg = problem.getDecomposedSubGoals().get(i);
                String prefix = i == problem.getDecomposedSubGoals().size() - 1 ? "      └─" : "      ├─";
                String deps = sg.dependsOn().isEmpty() ? "" : " (依赖: " + String.join(", ", sg.dependsOn()) + ")";
                System.out.println(prefix + " [" + sg.id() + "] " + sg.goal() + deps);
            }
        }
    }

    @Override
    public void onStrategyPlanning(ExecutionStrategy strategy) {
        System.out.println();
        System.out.println("🌳 Phase 2: 策略规划");

        var allCandidates = strategy.getAllCandidates();
        System.out.println("  ├─ 生成 " + allCandidates.size() + " 条候选策略:");

        for (int i = 0; i < allCandidates.size(); i++) {
            var scored = allCandidates.get(i);
            var s = scored.strategy();
            boolean isLast = i == allCandidates.size() - 1;
            String connector = isLast ? "  │   └─" : "  │   ├─";

            String marker = "";
            if (strategy.getPrimary() != null && s == strategy.getPrimary().strategy()) {
                marker = " ← 主路径";
            } else if (strategy.getFallback() != null && s == strategy.getFallback().strategy()) {
                marker = " ← 备用";
            }

            System.out.printf("%s %s: \"%s\" (score: %.2f)%s%n",
                    connector, s.getId(), s.getApproach(), scored.score(), marker);

            var breakdown = scored.breakdown();
            if (!breakdown.isEmpty()) {
                String breakdownStr = breakdown.entrySet().stream()
                        .map(e -> e.getKey() + "=" + String.format("%.2f", e.getValue()))
                        .collect(Collectors.joining(" + "));
                System.out.println("  │   │   └─ 评分: " + breakdownStr);
            }
        }

        if (strategy.getPrimary() != null) {
            var primarySteps = strategy.getPrimary().strategy().getSteps();
            if (!primarySteps.isEmpty()) {
                System.out.println("  └─ 主路径步骤:");
                for (int i = 0; i < primarySteps.size(); i++) {
                    var step = primarySteps.get(i);
                    boolean isLast = i == primarySteps.size() - 1;
                    String prefix = isLast ? "      └─" : "      ├─";
                    String tool = step.tool() != null ? " (工具: " + step.tool() + ")" : "";
                    String risk = "high".equals(step.risk()) ? " ⚠️" : "";
                    System.out.printf("%s %d. %s%s%s%n", prefix, i + 1, step.action(), tool, risk);
                }
            }
        }
    }

    @Override
    public void onMonitorAnalysis(int step, StepContext ctx, MonitorSignal signal) {
        if (signal.isHealthy()) {
            if (step % 5 == 0) {
                System.out.printf("%n📊 Phase 4: 监控 (步骤 %d) ✅ 正常%n", step);
            }
            return;
        }

        System.out.printf("%n📊 Phase 4: 监控 (步骤 %d)%n", step);
        for (var intervention : signal.getInterventions()) {
            String icon = switch (intervention.severity()) {
                case HIGH -> "🔴";
                case MEDIUM -> "🟡";
                case LOW -> "🟢";
            };
            System.out.printf("  %s [%s] %s%n", icon, intervention.type(), intervention.message());
        }
    }

    private String formatTaskType(ProblemSpec.TaskType type) {
        return switch (type) {
            case SINGLE_STEP -> "单步骤";
            case MULTI_STEP -> "多步骤";
            case EXPLORATORY -> "探索性";
            case OPEN_ENDED -> "开放性";
        };
    }

    private String formatComplexity(ProblemSpec.ComplexityLevel level) {
        return switch (level) {
            case SIMPLE -> "简单";
            case MEDIUM -> "中等";
            case COMPLEX -> "复杂";
        };
    }
}
