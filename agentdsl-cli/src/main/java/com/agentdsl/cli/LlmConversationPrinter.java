package com.agentdsl.cli;

import com.agentdsl.runtime.LlmCallListener;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;

/**
 * 终端彩色打印 LLM 调用的原始输入/输出。
 *
 * <p>当 CLI 以 {@code --verbose} 模式运行时，
 * 将此实例注入到 {@link com.agentdsl.runtime.AgentExecutor} 和
 * {@link com.agentdsl.runtime.autonomous.AutonomousExecutor} 中，
 * 即可在每次 LLM 调用前后打印完整的消息列表和模型回复。
 *
 * <h3>输出示例</h3>
 * <pre>
 * ┌─────────────────────────────────────────
 * │ LLM 调用 #1 · 发送消息
 * ├─────────────────────────────────────────
 *   [1] SYSTEM
 *     你是一个天气查询助手。
 *   [2] USER
 *     查一下天气
 * ├─────────── 可用工具 ────────────────────
 *   • weatherQuery: 查询指定城市的天气信息
 * └─────────────────────────────────────────
 * </pre>
 */
public class LlmConversationPrinter implements LlmCallListener {

    private int callCount = 0;

    @Override
    public void onRequest(List<ChatMessage> messages, List<ToolSpecification> tools) {
        callCount++;
        System.out.println();
        System.out.println(cyan("┌─────────────────────────────────────────"));
        System.out.println(cyan("│ LLM 调用 #" + callCount + " · 发送消息"));
        System.out.println(cyan("├─────────────────────────────────────────"));

        for (int i = 0; i < messages.size(); i++) {
            printMessage(i + 1, messages.get(i));
        }

        if (tools != null && !tools.isEmpty()) {
            System.out.println(cyan("├─────────── 可用工具 ────────────────────"));
            tools.forEach(t ->
                System.out.println(gray("  • " + t.name() + ": " +
                    truncate(t.description(), 60)))
            );
        }

        System.out.println(cyan("└─────────────────────────────────────────"));
        System.out.flush();
    }

    @Override
    public void onResponse(AiMessage aiMessage, long elapsedMs) {
        System.out.println(cyan("┌─────────────────────────────────────────"));
        System.out.println(cyan("│ LLM 回复 #" + callCount +
            " · 耗时 " + elapsedMs + "ms"));
        System.out.println(cyan("├─────────────────────────────────────────"));

        if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
            System.out.println(green("  [文本]"));
            printWrapped(aiMessage.text(), "  ");
        }

        if (aiMessage.hasToolExecutionRequests()) {
            System.out.println(yellow("  [工具调用]"));
            for (var req : aiMessage.toolExecutionRequests()) {
                System.out.println(yellow("  → " + req.name()));
                System.out.println(gray("    参数: " +
                    truncate(req.arguments(), 200)));
            }
        }

        System.out.println(cyan("└─────────────────────────────────────────"));
        System.out.println();
        System.out.flush();
    }

    // ────────────────────────────────────────────────────────────────
    // 消息渲染
    // ────────────────────────────────────────────────────────────────

    private void printMessage(int index, ChatMessage msg) {
        if (msg instanceof SystemMessage sm) {
            System.out.println(blue("  [" + index + "] SYSTEM"));
            printWrapped(sm.text(), "  ");

        } else if (msg instanceof UserMessage um) {
            String text = um.singleText();
            if (text != null) {
                boolean isInjected = text.startsWith("[系统提示]") ||
                                     text.startsWith("【历史执行摘要】");
                String label = isInjected
                        ? "  [" + index + "] USER(注入)"
                        : "  [" + index + "] USER";
                System.out.println(isInjected ? gray(label) : green(label));
                printWrapped(text, "  ");
            }

        } else if (msg instanceof AiMessage am) {
            System.out.println(yellow("  [" + index + "] ASSISTANT"));
            if (am.text() != null && !am.text().isBlank()) {
                printWrapped(am.text(), "  ");
            }
            if (am.hasToolExecutionRequests()) {
                am.toolExecutionRequests().forEach(req ->
                    System.out.println(yellow("    ⚙ " + req.name() +
                        "(" + truncate(req.arguments(), 60) + ")"))
                );
            }

        } else if (msg instanceof ToolExecutionResultMessage tr) {
            System.out.println(gray("  [" + index + "] TOOL(" +
                tr.toolName() + ")"));
            printWrapped(truncate(tr.text(), 300), "  ");
        }
    }

    /** 长文本按 100 字符自动换行，保持缩进。 */
    private void printWrapped(String text, String indent) {
        if (text == null) return;
        for (String line : text.split("\n", -1)) {
            while (line.length() > 100) {
                System.out.println(indent + "  " + line.substring(0, 100));
                line = line.substring(100);
            }
            System.out.println(indent + "  " + line);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // ANSI 颜色
    // ────────────────────────────────────────────────────────────────

    static String cyan(String s)   { return "\033[36m" + s + "\033[0m"; }
    static String green(String s)  { return "\033[32m" + s + "\033[0m"; }
    static String yellow(String s) { return "\033[33m" + s + "\033[0m"; }
    static String blue(String s)   { return "\033[34m" + s + "\033[0m"; }
    static String gray(String s)   { return "\033[90m" + s + "\033[0m"; }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
