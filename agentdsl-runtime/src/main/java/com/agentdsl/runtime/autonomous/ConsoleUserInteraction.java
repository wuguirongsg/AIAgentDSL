package com.agentdsl.runtime.autonomous;

import java.util.Scanner;

/**
 * 控制台交互实现。
 * CLI 场景下通过 System.in 读取用户输入实现计划确认和执行控制。
 */
public class ConsoleUserInteraction implements UserInteraction {

    private final Scanner scanner;

    public ConsoleUserInteraction() {
        this.scanner = new Scanner(System.in);
    }

    @Override
    public PlanFeedback confirmPlan(String plan) {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("📋 自主 Agent 执行计划");
        System.out.println("═".repeat(60));
        System.out.println(plan);
        System.out.println("─".repeat(60));
        System.out.println("请选择操作:");
        System.out.println("  [y] 确认执行此计划");
        System.out.println("  [m] 修改计划（输入修改建议）");
        System.out.println("  [n] 取消执行");
        System.out.print(">>> ");

        String input = scanner.nextLine().trim().toLowerCase();

        return switch (input) {
            case "y", "yes", "确认" -> new PlanFeedback(PlanFeedback.Action.APPROVE, null);
            case "m", "modify", "修改" -> {
                System.out.print("请输入修改建议: ");
                String suggestion = scanner.nextLine().trim();
                yield new PlanFeedback(PlanFeedback.Action.MODIFY, suggestion);
            }
            default -> new PlanFeedback(PlanFeedback.Action.REJECT, null);
        };
    }

    @Override
    public boolean confirmContinue(int currentStep, int maxSteps, String progressSummary) {
        System.out.println("\n" + "─".repeat(60));
        System.out.printf("⚠️  已执行 %d 步（达到 max_steps=%d 上限）%n", currentStep, maxSteps);
        if (progressSummary != null && !progressSummary.isBlank()) {
            System.out.println("📊 当前进度: " + progressSummary);
        }
        System.out.println("是否继续执行？");
        System.out.println("  [y] 继续执行（额外 " + maxSteps + " 步）");
        System.out.println("  [n] 终止执行");
        System.out.print(">>> ");

        String input = scanner.nextLine().trim().toLowerCase();
        return "y".equals(input) || "yes".equals(input) || "继续".equals(input);
    }

    @Override
    public void showProgress(String message) {
        System.out.println(message);
    }
}
