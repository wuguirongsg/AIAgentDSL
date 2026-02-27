package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全沙箱测试。
 * 验证沙箱模式下恶意脚本被拦截。
 */
class SecuritySandboxTest {

    private final DslCompiler sandboxCompiler = new DslCompiler(true, 3);

    @Test
    @DisplayName("沙箱模式：System.exit 应被拦截")
    void shouldBlockSystemExit() {
        String dsl = """
                    System.exit(0)
                """;

        assertThrows(DslCompilationException.class, () -> sandboxCompiler.compile(dsl));
    }

    @Test
    @DisplayName("沙箱模式：Runtime.exec 应被拦截")
    void shouldBlockRuntimeExec() {
        String dsl = """
                    Runtime.runtime.exec("ls")
                """;

        assertThrows(DslCompilationException.class, () -> sandboxCompiler.compile(dsl));
    }

    @Test
    @DisplayName("沙箱模式：ProcessBuilder 应被拦截")
    void shouldBlockProcessBuilder() {
        String dsl = """
                    new ProcessBuilder("cmd").start()
                """;

        assertThrows(DslCompilationException.class, () -> sandboxCompiler.compile(dsl));
    }

    @Test
    @DisplayName("沙箱模式：文件访问应被拦截")
    void shouldBlockFileAccess() {
        String dsl = """
                    new File("/etc/passwd").text
                """;

        assertThrows(DslCompilationException.class, () -> sandboxCompiler.compile(dsl));
    }

    @Test
    @DisplayName("沙箱模式：网络访问应被拦截")
    void shouldBlockNetworkAccess() {
        String dsl = """
                    new URL("http://evil.com").text
                """;

        assertThrows(DslCompilationException.class, () -> sandboxCompiler.compile(dsl));
    }

    @Test
    @DisplayName("沙箱模式：超时控制 — 无限循环应被终止")
    void shouldTimeoutLongRunningScript() {
        String dsl = """
                    def x = 0
                    while(true) { x = x + 1 }
                """;

        DslCompilationException ex = assertThrows(DslCompilationException.class,
                () -> sandboxCompiler.compile(dsl));
        assertTrue(ex.getMessage().contains("超时") || ex.getMessage().contains("timeout")
                || ex.getErrorCode().equals("ADSL-003"),
                "应抛出超时相关异常，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("沙箱模式：正常 DSL 脚本应正常工作")
    void shouldAllowNormalDsl() {
        String dsl = """
                    agent("safe-agent") {
                        model {
                            provider "ollama"
                            modelName "qwen:0.5b-chat"
                        }
                        systemPrompt "你好"
                    }
                """;

        var result = sandboxCompiler.compile(dsl);
        assertNotNull(result);
        assertEquals(1, result.getAgents().size());
        assertEquals("safe-agent", result.getAgents().get(0).getName());
    }
}
