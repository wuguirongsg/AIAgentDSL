package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自主 Agent (Autonomous) DSL 解析和校验测试。
 */
class AutonomousDslTest {

    private DslCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DslCompiler();
    }

    @Nested
    @DisplayName("Autonomous DSL 解析")
    class AutonomousParsing {

        @Test
        @DisplayName("Plan 模式解析")
        void shouldParseAutonomousPlanMode() {
            String dsl = """
                    agent("auto-plan") {
                        model {
                            provider "ollama"
                            modelName "qwen3:14b"
                        }
                        autonomous {
                            execution_mode "plan"
                            max_steps 10
                        }
                        systemPrompt "你是一个自主助手"
                    }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertNotNull(agent.getAutonomous());
            assertTrue(agent.isAutonomous());
            assertEquals("plan", agent.getAutonomous().getExecutionMode());
            assertEquals(10, agent.getAutonomous().getMaxSteps());
            assertTrue(agent.getAutonomous().isPlanMode());
            assertFalse(agent.getAutonomous().isFastMode());
        }

        @Test
        @DisplayName("Fast 模式解析")
        void shouldParseAutonomousFastMode() {
            String dsl = """
                    agent("auto-fast") {
                        model {
                            provider "ollama"
                            modelName "qwen3:4b"
                        }
                        autonomous {
                            execution_mode "fast"
                            max_steps 5
                        }
                    }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertNotNull(agent.getAutonomous());
            assertEquals("fast", agent.getAutonomous().getExecutionMode());
            assertEquals(5, agent.getAutonomous().getMaxSteps());
            assertTrue(agent.getAutonomous().isFastMode());
            assertFalse(agent.getAutonomous().isPlanMode());
        }

        @Test
        @DisplayName("Autonomous 默认值")
        void shouldApplyAutonomousDefaults() {
            String dsl = """
                    agent("auto-default") {
                        model {
                            provider "ollama"
                            modelName "qwen3:4b"
                        }
                        autonomous {
                        }
                    }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertNotNull(agent.getAutonomous());
            assertEquals("plan", agent.getAutonomous().getExecutionMode());
            assertEquals(10, agent.getAutonomous().getMaxSteps());
        }

        @Test
        @DisplayName("非自主 Agent 无 autonomous 字段")
        void shouldHaveNoAutonomousForNormalAgent() {
            String dsl = """
                    agent("normal") {
                        model {
                            provider "ollama"
                            modelName "qwen3:4b"
                        }
                    }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertNull(agent.getAutonomous());
            assertFalse(agent.isAutonomous());
        }

        @Test
        @DisplayName("自主 Agent 带 tools 配置")
        void shouldParseAutonomousWithTools() {
            String dsl = """
                    agent("auto-tools") {
                        model {
                            provider "ollama"
                            modelName "qwen3:14b"
                        }
                        autonomous {
                            execution_mode "plan"
                            max_steps 8
                        }
                        tools {
                            tool("test-tool") {
                                description "测试工具"
                                execute { -> "ok" }
                            }
                        }
                    }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertTrue(agent.isAutonomous());
            assertEquals("plan", agent.getAutonomous().getExecutionMode());
            assertEquals(8, agent.getAutonomous().getMaxSteps());
            assertEquals(1, agent.getTools().size());
        }
    }

    @Nested
    @DisplayName("Autonomous 校验")
    class AutonomousValidation {

        @Test
        @DisplayName("无效的 execution_mode 应抛出异常")
        void shouldRejectInvalidExecutionMode() {
            String dsl = """
                    agent("bad-mode") {
                        model {
                            provider "ollama"
                            modelName "qwen3:4b"
                        }
                        autonomous {
                            execution_mode "invalid"
                        }
                    }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("execution_mode"));
            assertTrue(ex.getMessage().contains("plan") || ex.getMessage().contains("fast"));
        }

        @Test
        @DisplayName("max_steps <= 0 应抛出异常")
        void shouldRejectZeroMaxSteps() {
            String dsl = """
                    agent("bad-steps") {
                        model {
                            provider "ollama"
                            modelName "qwen3:4b"
                        }
                        autonomous {
                            execution_mode "fast"
                            max_steps 0
                        }
                    }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("max_steps"));
        }

        @Test
        @DisplayName("自主 Agent 无工具应产生软性警告")
        void shouldWarnAutonomousAgentWithoutTools() {
            String dsl = """
                    agent("no-tools") {
                        model {
                            provider "ollama"
                            modelName "qwen3:4b"
                        }
                        autonomous {
                            execution_mode "plan"
                            max_steps 5
                        }
                    }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            // 应该成功编译但有警告
            assertNotNull(result.getFirstAgent());
            assertTrue(result.getFirstAgent().isAutonomous());

            // 检查诊断警告
            List<Diagnostic> diagnostics = result.getDiagnostics();
            assertTrue(diagnostics.stream()
                    .anyMatch(d -> d.getMessage().contains("tools") || d.getMessage().contains("skills")),
                    "应该有关于自主 Agent 缺少工具的警告");
        }
    }
}
