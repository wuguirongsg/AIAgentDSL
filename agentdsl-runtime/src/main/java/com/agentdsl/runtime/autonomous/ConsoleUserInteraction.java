package com.agentdsl.runtime.autonomous;

import java.util.Scanner;
import java.util.Set;

public class ConsoleUserInteraction implements UserInteraction {

    private static final Set<String> EXIT_COMMANDS = Set.of("/exit", "/quit", "q", "exit", "quit");
    private static final Set<String> HELP_COMMANDS = Set.of("/help", "help");

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
        System.out.print(message);
    }

    @Override
    public String readGoal() {
        System.out.print("\n\033[36m➜\033[0m ");
        return scanner.nextLine();
    }

    @Override
    public void showResult(AutonomousResult result) {
        System.out.println();
        System.out.println("═".repeat(60));
        System.out.println(result.getFinalAnswer());
        System.out.println("═".repeat(60));
        System.out.printf("📊 执行了 %d 步，%s%n",
                result.getTotalSteps(),
                result.isCompleted() ? "✅ 目标已完成" : "⚠️ " + result.getTerminationReason());
    }

    @Override
    public void showWelcome(String agentName) {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║        🤖 AgentDSL Autonomous 交互式模式                  ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║  Agent: " + padRight(agentName, 48) + "║");
        System.out.println("║  输入任务目标，或 /help 查看命令帮助                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    @Override
    public void showGoodbye() {
        System.out.println();
        System.out.println("👋 对话结束，会话历史已保存。");
    }

    @Override
    public boolean isExitCommand(String input) {
        return EXIT_COMMANDS.contains(input.trim().toLowerCase());
    }

    @Override
    public boolean isHelpCommand(String input) {
        return HELP_COMMANDS.contains(input.trim().toLowerCase());
    }

    @Override
    public void showHelp() {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ 📖 可用命令                         │");
        System.out.println("├─────────────────────────────────────┤");
        System.out.println("│ /exit, /quit, q   - 退出对话        │");
        System.out.println("│ /help             - 显示帮助        │");
        System.out.println("└─────────────────────────────────────┘");
    }

    private String padRight(String s, int length) {
        return String.format("%-" + length + "s", s);
    }
}
