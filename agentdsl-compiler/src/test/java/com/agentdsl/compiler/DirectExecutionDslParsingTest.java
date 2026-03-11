package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.StepSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow 直接执行模式 DSL 解析测试。
 * 验证 execute / tool / skill / mcp 语法的各种形式可正确解析为 StepSpec。
 */
class DirectExecutionDslParsingTest {

    private DslCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DslCompiler();
    }

    @Nested
    @DisplayName("execute 纯代码执行模式解析")
    class ExecuteModeParsing {

        @Test
        @DisplayName("基本 execute 闭包解析")
        void shouldParseExecuteStep() {
            String dsl = """
                        workflow("exec-test") {
                            steps {
                                step("process") {
                                    execute { ctx ->
                                        return "computed-result"
                                    }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            WorkflowSpec workflow = result.getFirstWorkflow();

            assertEquals(1, workflow.getSteps().size());
            StepSpec step = workflow.getSteps().get(0);
            assertEquals(StepSpec.StepType.SEQUENTIAL, step.getType());
            assertEquals("process", step.getName());
            assertNotNull(step.getExecuteClosure());
            assertNull(step.getAgentRef());
            assertNull(step.getToolRef());
            assertTrue(step.isDirectExecution());
            assertEquals("execute", step.getExecutionMode());
        }

        @Test
        @DisplayName("execute 闭包搭配 output 转换")
        void shouldParseExecuteWithOutputTransform() {
            String dsl = """
                        workflow("exec-output-test") {
                            steps {
                                step("compute") {
                                    execute { ctx ->
                                        return 42
                                    }
                                    output { result -> "value=" + result }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            StepSpec step = result.getFirstWorkflow().getSteps().get(0);

            assertNotNull(step.getExecuteClosure());
            assertNotNull(step.getOutputTransform());
        }
    }

    @Nested
    @DisplayName("tool 直接工具调用模式解析")
    class ToolModeParsing {

        @Test
        @DisplayName("基本 tool 引用解析")
        void shouldParseToolStep() {
            String dsl = """
                        workflow("tool-test") {
                            steps {
                                step("fetch") {
                                    tool "http_get"
                                    input { lastOutput -> [url: "https://example.com"] }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            StepSpec step = result.getFirstWorkflow().getSteps().get(0);

            assertEquals("http_get", step.getToolRef());
            assertNull(step.getAgentRef());
            assertNull(step.getExecuteClosure());
            assertNotNull(step.getInputTransform());
            assertTrue(step.isDirectExecution());
            assertEquals("tool:http_get", step.getExecutionMode());
        }
    }

    @Nested
    @DisplayName("skill 直接技能调用模式解析")
    class SkillModeParsing {

        @Test
        @DisplayName("基本 skill 引用解析")
        void shouldParseSkillStep() {
            String dsl = """
                        workflow("skill-test") {
                            steps {
                                step("process") {
                                    skill "data_processor"
                                    input { lastOutput -> [data: lastOutput] }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            StepSpec step = result.getFirstWorkflow().getSteps().get(0);

            assertEquals("data_processor", step.getSkillRef());
            assertNull(step.getAgentRef());
            assertNull(step.getToolRef());
            assertNotNull(step.getInputTransform());
            assertTrue(step.isDirectExecution());
            assertEquals("skill:data_processor", step.getExecutionMode());
        }
    }

    @Nested
    @DisplayName("mcp 直接 MCP 调用模式解析")
    class McpModeParsing {

        @Test
        @DisplayName("基本 mcp 引用解析")
        void shouldParseMcpStep() {
            String dsl = """
                        workflow("mcp-test") {
                            steps {
                                step("read-file") {
                                    mcp "github_mcp", "get_file_contents"
                                    input { lastOutput -> [repo: "company/data", path: "config.json"] }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            StepSpec step = result.getFirstWorkflow().getSteps().get(0);

            assertEquals("github_mcp", step.getMcpServerRef());
            assertEquals("get_file_contents", step.getMcpToolRef());
            assertNull(step.getAgentRef());
            assertNotNull(step.getInputTransform());
            assertTrue(step.isDirectExecution());
            assertTrue(step.getExecutionMode().startsWith("mcp:"));
        }
    }

    @Nested
    @DisplayName("混合编排模式解析")
    class HybridOrchestrationParsing {

        @Test
        @DisplayName("execute + agent + tool 混合编排")
        void shouldParseHybridWorkflow() {
            String dsl = """
                        workflow("hybrid-test") {
                            steps {
                                step("fetch-data") {
                                    execute { ctx ->
                                        return "raw-data"
                                    }
                                }
                                step("analyze") {
                                    agent "analyzer"
                                    input { data -> "分析: " + data }
                                }
                                step("notify") {
                                    tool "http_post"
                                    input { result -> [url: "https://notify.com", body: result] }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            WorkflowSpec workflow = result.getFirstWorkflow();

            assertEquals(3, workflow.getSteps().size());

            // Step 1: execute
            StepSpec step1 = workflow.getSteps().get(0);
            assertNotNull(step1.getExecuteClosure());
            assertNull(step1.getAgentRef());

            // Step 2: agent
            StepSpec step2 = workflow.getSteps().get(1);
            assertEquals("analyzer", step2.getAgentRef());
            assertNull(step2.getExecuteClosure());
            assertFalse(step2.isDirectExecution());

            // Step 3: tool
            StepSpec step3 = workflow.getSteps().get(2);
            assertEquals("http_post", step3.getToolRef());
            assertNull(step3.getAgentRef());
        }

        @Test
        @DisplayName("parallel 中使用 execute 和 tool 混合")
        void shouldParseParallelWithMixedModes() {
            String dsl = """
                        workflow("parallel-mixed") {
                            steps {
                                parallel {
                                    step("compute") {
                                        execute { ctx -> return "computed" }
                                    }
                                    step("fetch") {
                                        tool "http_get"
                                        input { prev -> [url: "https://api.com"] }
                                    }
                                    step("think") {
                                        agent "thinker"
                                    }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            StepSpec parallel = result.getFirstWorkflow().getSteps().get(0);

            assertEquals(StepSpec.StepType.PARALLEL, parallel.getType());
            assertEquals(3, parallel.getParallelSteps().size());

            assertNotNull(parallel.getParallelSteps().get(0).getExecuteClosure());
            assertEquals("http_get", parallel.getParallelSteps().get(1).getToolRef());
            assertEquals("thinker", parallel.getParallelSteps().get(2).getAgentRef());
        }

        @Test
        @DisplayName("loop 中使用 execute 模式")
        void shouldParseLoopWithExecuteMode() {
            String dsl = """
                        workflow("loop-exec") {
                            steps {
                                loop(maxIterations: 5) {
                                    step("process") {
                                        execute { ctx ->
                                            return "iteration-result"
                                        }
                                    }
                                    until { result -> result == "done" }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            StepSpec loop = result.getFirstWorkflow().getSteps().get(0);

            assertEquals(StepSpec.StepType.LOOP, loop.getType());
            assertEquals(1, loop.getLoopBody().size());
            assertNotNull(loop.getLoopBody().get(0).getExecuteClosure());
        }
    }

    @Nested
    @DisplayName("校验互斥性")
    class ExecutionModeValidation {

        @Test
        @DisplayName("agent 与 execute 同时存在应抛异常")
        void shouldRejectAgentAndExecuteTogether() {
            String dsl = """
                        workflow("invalid") {
                            steps {
                                step("conflict") {
                                    agent "some-agent"
                                    execute { ctx -> "result" }
                                }
                            }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-004"));
            assertTrue(ex.getMessage().contains("互斥"));
        }

        @Test
        @DisplayName("agent 与 tool 同时存在应抛异常")
        void shouldRejectAgentAndToolTogether() {
            String dsl = """
                        workflow("invalid") {
                            steps {
                                step("conflict") {
                                    agent "some-agent"
                                    tool "http_get"
                                }
                            }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-004"));
        }

        @Test
        @DisplayName("步骤未指定任何执行模式应抛异常")
        void shouldRejectEmptyExecutionMode() {
            String dsl = """
                        workflow("invalid") {
                            steps {
                                step("empty") {
                                    input { prev -> prev }
                                }
                            }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-004"));
            assertTrue(ex.getMessage().contains("未指定执行模式"));
        }
    }
}
