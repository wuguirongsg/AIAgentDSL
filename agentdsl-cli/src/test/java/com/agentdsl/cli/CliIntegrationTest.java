package com.agentdsl.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CLI 集成测试")
class CliIntegrationTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("RunCommand - 缺少必需参数应报错并返回 1")
    void testRunCommandMissingArgs(@TempDir Path tempDir) throws Exception {
        Path scriptFile = tempDir.resolve("test.agent.groovy");
        String dsl = """
                    agent("testAgent") {
                        model { provider "ollama"; modelName "qwen2.5" }
                    }
                """;
        Files.writeString(scriptFile, dsl);

        RunCommand runCommand = new RunCommand();
        CommandLine cmd = new CommandLine(runCommand);

        int exitCode = cmd.execute(scriptFile.toString());
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("请指定 --chat"));
    }

    @Test
    @DisplayName("ValidateCommand - 校验合法的脚本应输出成功并返回 0")
    void testValidateCommandSuccess(@TempDir Path tempDir) throws Exception {
        Path scriptFile = tempDir.resolve("valid.agent.groovy");
        String dsl = """
                    agent("testAgent") {
                        model { provider "ollama"; modelName "qwen2.5" }
                    }
                """;
        Files.writeString(scriptFile, dsl);

        ValidateCommand validateCommand = new ValidateCommand();
        CommandLine cmd = new CommandLine(validateCommand);

        int exitCode = cmd.execute(scriptFile.toString());
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("语法校验通过"));
    }

    @Test
    @DisplayName("ValidateCommand - 校验失败包含 Diagnostics 和异常")
    void testValidateCommandFailureWithWarning(@TempDir Path tempDir) throws Exception {
        Path scriptFile = tempDir.resolve("warning.agent.groovy");
        String dsl = """
                    tool("testTool") {
                        description "A tool"
                        returns "unknown"
                        execute { -> "ok" }
                    }
                    agent("testAgent") {
                        model { provider "ollama"; modelName "qwen2.5" }
                    }
                """;
        Files.writeString(scriptFile, dsl);

        ValidateCommand validateCommand = new ValidateCommand();
        CommandLine cmd = new CommandLine(validateCommand);

        int exitCode = cmd.execute(scriptFile.toString());
        originalOut.println("Validation Output: " + outContent.toString());
        originalOut.println("Validation Error: " + errContent.toString());
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("语法校验通过"));
        assertTrue(outContent.toString().contains("Compilation Warnings"));
        assertTrue(outContent.toString().contains("unknown")); // Diagnostics Warning
    }

    @Test
    @DisplayName("ValidateCommand - 校验失败包含 Exception")
    void testValidateCommandException(@TempDir Path tempDir) throws Exception {
        Path scriptFile = tempDir.resolve("invalid.agent.groovy");
        String dsl = """
                    agent("testAgent") {
                        // missing model block
                    }
                """;
        Files.writeString(scriptFile, dsl);

        ValidateCommand validateCommand = new ValidateCommand();
        CommandLine cmd = new CommandLine(validateCommand);

        int exitCode = cmd.execute(scriptFile.toString());
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("语法校验失败"));
        assertTrue(errContent.toString().contains("ADSL-001"));
    }

    @Test
    @DisplayName("ListCommand - 列出脚本中的模型和工具应返回 0")
    void testListCommandSuccess(@TempDir Path tempDir) throws Exception {
        Path scriptFile = tempDir.resolve("list.agent.groovy");
        String dsl = """
                    tool("fetchData") {
                        description "获取数据"
                        execute { -> "data" }
                    }
                    agent("dataBot") {
                        model { provider "ollama"; modelName "qwen2.5" }
                        systemPrompt "I am a bot"
                    }
                """;
        Files.writeString(scriptFile, dsl);

        ListCommand listCommand = new ListCommand();
        CommandLine cmd = new CommandLine(listCommand);

        int exitCode = cmd.execute(scriptFile.toString());
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("dataBot"));
        assertTrue(output.contains("fetchData"));
    }
}
