package com.agentdsl.cli;

import dev.langchain4j.data.message.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmConversationPrinter 单元测试")
class LlmConversationPrinterTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private LlmConversationPrinter printer;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(out));
        printer = new LlmConversationPrinter();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("onRequest 应输出调用序号和消息列表")
    void testOnRequestPrintsCallCountAndMessages() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("你是一个助手"),
                UserMessage.from("你好")
        );
        printer.onRequest(messages, List.of());

        String output = out.toString();
        assertTrue(output.contains("LLM 调用 #1"), "应包含调用序号 #1");
        assertTrue(output.contains("SYSTEM"), "应包含 SYSTEM 消息标签");
        assertTrue(output.contains("你是一个助手"), "应包含 system prompt 内容");
        assertTrue(output.contains("USER"), "应包含 USER 消息标签");
        assertTrue(output.contains("你好"), "应包含用户消息内容");
    }

    @Test
    @DisplayName("onRequest 应输出可用工具列表")
    void testOnRequestPrintsToolList() {
        ToolSpecification tool = ToolSpecification.builder()
                .name("weatherQuery")
                .description("查询天气")
                .build();

        printer.onRequest(List.of(UserMessage.from("test")), List.of(tool));

        String output = out.toString();
        assertTrue(output.contains("可用工具"), "应包含可用工具标题");
        assertTrue(output.contains("weatherQuery"), "应包含工具名称");
        assertTrue(output.contains("查询天气"), "应包含工具描述");
    }

    @Test
    @DisplayName("onResponse 应输出回复序号、耗时和文本内容")
    void testOnResponsePrintsTextContent() {
        // 先调用 onRequest 以推进 callCount
        printer.onRequest(List.of(UserMessage.from("test")), List.of());
        out.reset();

        AiMessage aiMessage = AiMessage.from("北京天气晴朗，25°C。");
        printer.onResponse(aiMessage, 512);

        String output = out.toString();
        assertTrue(output.contains("LLM 回复 #1"), "应包含回复序号 #1");
        assertTrue(output.contains("512ms"), "应包含耗时");
        assertTrue(output.contains("[文本]"), "应包含文本标签");
        assertTrue(output.contains("北京天气晴朗"), "应包含回复文本内容");
    }

    @Test
    @DisplayName("onRequest 调用两次后序号应递增为 #2")
    void testCallCountIncrementsOnSecondRequest() {
        printer.onRequest(List.of(UserMessage.from("第一次")), List.of());
        out.reset();
        printer.onRequest(List.of(UserMessage.from("第二次")), List.of());

        String output = out.toString();
        assertTrue(output.contains("LLM 调用 #2"), "第二次 onRequest 序号应为 #2");
    }

    @Test
    @DisplayName("onRequest 注入消息（[系统提示]开头）应标注为 USER(注入)")
    void testInjectedUserMessageLabel() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("[系统提示] 如果任务已完成请输出 TASK_COMPLETE")
        );
        printer.onRequest(messages, List.of());

        String output = out.toString();
        assertTrue(output.contains("USER(注入)"), "注入消息应标注为 USER(注入)");
    }

    @Test
    @DisplayName("ANSI 颜色代码应包含在输出中")
    void testAnsiCodesPresent() {
        printer.onRequest(List.of(UserMessage.from("hello")), List.of());
        String output = out.toString();
        assertTrue(output.contains("\033["), "输出应包含 ANSI 转义码");
    }

    @Test
    @DisplayName("超过 100 字符的文本应被截断换行")
    void testLongTextIsTruncated() {
        String longText = "A".repeat(150);
        printer.onRequest(List.of(UserMessage.from(longText)), List.of());
        String output = out.toString();
        // 应能找到两次缩进起始（说明文本被分成至少两行打印）
        long lines = output.lines()
                .filter(l -> l.contains("  ") && l.trim().startsWith("A"))
                .count();
        assertTrue(lines >= 2, "长文本应被换行为至少 2 行，实际行数: " + lines);
    }
}
