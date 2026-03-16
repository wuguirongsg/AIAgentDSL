package com.agentdsl.cli;

import com.agentdsl.runtime.AgentDslEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class InteractiveSession {

    private static final Set<String> EXIT_COMMANDS = Set.of("/exit", "/quit", "q", "exit", "quit");
    private static final Set<String> HELP_COMMANDS = Set.of("/help", "help");
    private static final Set<String> CLEAR_COMMANDS = Set.of("/clear", "clear");
    private static final Set<String> RESTART_COMMANDS = Set.of("/restart", "restart");

    private final AgentDslEngine engine;
    private final String agentName;
    private final BufferedReader reader;
    private final PrintWriter writer;

    public InteractiveSession(AgentDslEngine engine, String agentName) {
        this.engine = engine;
        this.agentName = agentName;
        this.reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
    }

    public void run() {
        printWelcome();
        
        try {
            String initialGreeting = engine.chat(agentName, "你好");
            if (initialGreeting != null && !initialGreeting.isBlank()) {
                printAgentMessage(initialGreeting);
            }
        } catch (Exception e) {
        }

        while (true) {
            try {
                String input = readInput();
                
                if (input == null) {
                    printGoodbye();
                    break;
                }
                
                input = input.trim();
                
                if (input.isEmpty()) {
                    continue;
                }
                
                if (isExitCommand(input)) {
                    printGoodbye();
                    break;
                }
                
                if (isHelpCommand(input)) {
                    printHelp();
                    continue;
                }
                
                if (isClearCommand(input)) {
                    clearScreen();
                    continue;
                }
                
                if (isRestartCommand(input)) {
                    printRestart();
                    continue;
                }
                
                printUserMessage(input);
                String response = engine.chat(agentName, input);
                printAgentMessage(response);
                
            } catch (IOException e) {
                writer.println("❌ 读取输入失败: " + e.getMessage());
            } catch (Exception e) {
                writer.println("❌ 执行失败: " + e.getMessage());
            }
        }
    }

    private void printWelcome() {
        writer.println();
        writer.println("╔════════════════════════════════════════════════════════════╗");
        writer.println("║           🤖 AgentDSL 交互式对话模式                        ║");
        writer.println("╠════════════════════════════════════════════════════════════╣");
        writer.println("║  Agent: " + padRight(agentName, 48) + "║");
        writer.println("║  输入 /help 查看命令帮助                                   ║");
        writer.println("╚════════════════════════════════════════════════════════════╝");
        writer.println();
    }

    private void printHelp() {
        writer.println();
        writer.println("┌─────────────────────────────────────┐");
        writer.println("│ 📖 可用命令                         │");
        writer.println("├─────────────────────────────────────┤");
        writer.println("│ /exit, /quit, q   - 退出对话         │");
        writer.println("│ /help             - 显示帮助        │");
        writer.println("│ /clear            - 清屏           │");
        writer.println("│ /restart          - 重新加载 Agent  │");
        writer.println("└─────────────────────────────────────┘");
        writer.println();
    }

    private void printGoodbye() {
        writer.println();
        writer.println("👋 对话结束，会话历史已保存。");
        writer.println();
    }

    private void printRestart() {
        writer.println("🔄 重新加载功能开发中...");
    }

    private void clearScreen() {
        writer.print("\033[H\033[2J");
        writer.flush();
        printWelcome();
    }

    private String readInput() throws IOException {
        writer.print("\033[36m➜\033[0m ");
        writer.flush();
        return reader.readLine();
    }

    private void printUserMessage(String message) {
        writer.println("\033[33m[你]\033[0m " + message);
    }

    private void printAgentMessage(String message) {
        writer.println();
        writer.println("\033[32m[Agent]\033[0m " + message);
        writer.println();
    }

    private boolean isExitCommand(String input) {
        return EXIT_COMMANDS.contains(input.toLowerCase());
    }

    private boolean isHelpCommand(String input) {
        return HELP_COMMANDS.contains(input.toLowerCase());
    }

    private boolean isClearCommand(String input) {
        return CLEAR_COMMANDS.contains(input.toLowerCase());
    }

    private boolean isRestartCommand(String input) {
        return RESTART_COMMANDS.contains(input.toLowerCase());
    }

    private String padRight(String s, int length) {
        return String.format("%-" + length + "s", s);
    }
}
