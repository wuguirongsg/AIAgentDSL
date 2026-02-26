package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.WorkflowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 工作流执行引擎测试。
 * 使用 Mock 的 AgentExecutor 验证工作流执行逻辑。
 */
class WorkflowExecutorTest {

    private DslCompiler compiler;
    private AgentRegistry registry;
    private AgentExecutor agentExecutor;
    private WorkflowExecutor workflowExecutor;

    @BeforeEach
    void setUp() {
        compiler = new DslCompiler();
        registry = mock(AgentRegistry.class);
        agentExecutor = mock(AgentExecutor.class);
        workflowExecutor = new WorkflowExecutor(agentExecutor, registry);
    }

    private WorkflowSpec compileWorkflow(String dsl) {
        DslCompileResult result = compiler.compile(dsl);
        WorkflowSpec workflow = result.getFirstWorkflow();
        // 将 workflow 注册到 mock registry
        when(registry.getWorkflow(workflow.getName())).thenReturn(workflow);
        return workflow;
    }

    @Nested
    @DisplayName("顺序执行")
    class SequentialExecution {

        @Test
        @DisplayName("基本顺序工作流 — 数据在步骤间传递")
        void shouldExecuteSequentialSteps() {
            String dsl = """
                        workflow("seq-test") {
                            steps {
                                step("step1") {
                                    agent "agent-a"
                                }
                                step("step2") {
                                    agent "agent-b"
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileWorkflow(dsl);

            // Mock agent 返回
            when(agentExecutor.chat(eq("agent-a"), anyString())).thenReturn("output-A");
            when(agentExecutor.chat(eq("agent-b"), eq("output-A"))).thenReturn("output-B");

            WorkflowResult result = workflowExecutor.execute(workflow, "hello");

            assertEquals("output-B", result.getFinalOutputAsString());
            assertEquals("output-A", result.getStepResult("step1"));
            assertEquals("output-B", result.getStepResult("step2"));

            // 验证调用顺序
            verify(agentExecutor).chat("agent-a", "hello");
            verify(agentExecutor).chat("agent-b", "output-A");
        }

        @Test
        @DisplayName("带 input/output 转换闭包")
        void shouldApplyInputOutputTransform() {
            String dsl = """
                        workflow("transform-test") {
                            steps {
                                step("translate") {
                                    agent "translator"
                                    input { text -> "translate: " + text }
                                }
                                step("review") {
                                    agent "reviewer"
                                    input { prev -> "review: " + prev }
                                    output { result -> "final:" + result }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileWorkflow(dsl);

            when(agentExecutor.chat(eq("translator"), eq("translate: hello")))
                    .thenReturn("translated-text");
            when(agentExecutor.chat(eq("reviewer"), eq("review: translated-text")))
                    .thenReturn("reviewed-text");

            WorkflowResult result = workflowExecutor.execute(workflow, "hello");

            // output 闭包转换结果
            assertEquals("final:reviewed-text", result.getFinalOutputAsString());
        }
    }

    @Nested
    @DisplayName("条件路由")
    class ConditionExecution {

        @Test
        @DisplayName("条件路由 — 匹配 L1 分支")
        void shouldRouteToMatchingBranch() {
            String dsl = """
                        workflow("cond-test") {
                            steps {
                                step("classify") {
                                    agent "classifier"
                                }

                                condition {
                                    check { result -> result }

                                    on("L1") {
                                        step("simple") {
                                            agent "simple-agent"
                                        }
                                    }

                                    on("L2") {
                                        step("complex") {
                                            agent "complex-agent"
                                        }
                                    }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileWorkflow(dsl);

            // classifier 返回 "L1"
            when(agentExecutor.chat(eq("classifier"), anyString())).thenReturn("L1");
            when(agentExecutor.chat(eq("simple-agent"), eq("L1"))).thenReturn("simple-done");

            WorkflowResult result = workflowExecutor.execute(workflow, "ticket");

            assertEquals("simple-done", result.getFinalOutputAsString());
            assertNotNull(result.getStepResult("simple"));
            assertNull(result.getStepResult("complex")); // L2 分支未执行

            verify(agentExecutor, never()).chat(eq("complex-agent"), anyString());
        }
    }

    @Nested
    @DisplayName("循环执行")
    class LoopExecution {

        @Test
        @DisplayName("循环 — until 条件提前终止")
        void shouldStopWhenUntilConditionMet() {
            String dsl = """
                        workflow("loop-test") {
                            steps {
                                loop(maxIterations: 5) {
                                    step("check") {
                                        agent "checker"
                                    }

                                    until { result -> result == "done" }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileWorkflow(dsl);

            // 前两次返回 "not-done"，第三次返回 "done"
            when(agentExecutor.chat(eq("checker"), anyString()))
                    .thenReturn("not-done")
                    .thenReturn("not-done")
                    .thenReturn("done");

            WorkflowResult result = workflowExecutor.execute(workflow, "start");

            assertEquals("done", result.getFinalOutputAsString());
            verify(agentExecutor, times(3)).chat(eq("checker"), anyString());
        }

        @Test
        @DisplayName("循环 — 达到 maxIterations 自动停止")
        void shouldStopAtMaxIterations() {
            String dsl = """
                        workflow("max-loop-test") {
                            steps {
                                loop(maxIterations: 3) {
                                    step("iterate") {
                                        agent "iterator"
                                    }

                                    until { result -> false }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileWorkflow(dsl);

            when(agentExecutor.chat(eq("iterator"), anyString()))
                    .thenReturn("iter-result");

            workflowExecutor.execute(workflow, "start");

            // 应该恰好执行 3 次
            verify(agentExecutor, times(3)).chat(eq("iterator"), anyString());
        }
    }

    @Nested
    @DisplayName("通过 AgentDslEngine 执行")
    class EngineIntegration {

        @Test
        @DisplayName("通过名称执行已注册的工作流")
        void shouldExecuteByName() {
            String dsl = """
                        workflow("named-wf") {
                            steps {
                                step("single") {
                                    agent "my-agent"
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileWorkflow(dsl);

            when(agentExecutor.chat(eq("my-agent"), anyString())).thenReturn("result");

            WorkflowResult result = workflowExecutor.execute("named-wf", "input");

            assertEquals("result", result.getFinalOutputAsString());
        }
    }
}
