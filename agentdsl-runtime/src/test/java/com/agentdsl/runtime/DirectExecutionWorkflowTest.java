package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Workflow 直接执行模式（execute/tool/skill）的运行时测试。
 * 验证绕过 LLM 的执行逻辑正确性。
 */
class DirectExecutionWorkflowTest {

    private DslCompiler compiler;
    private AgentRegistry registry;
    private AgentExecutor agentExecutor;
    private WorkflowExecutor workflowExecutor;

    @BeforeEach
    void setUp() {
        compiler = new DslCompiler();
        registry = new AgentRegistry();
        agentExecutor = mock(AgentExecutor.class);
        workflowExecutor = new WorkflowExecutor(agentExecutor, registry);
    }

    private WorkflowSpec compileAndRegisterWorkflow(String dsl) {
        DslCompileResult result = compiler.compile(dsl);
        // 注册全局工具
        if (result.getTools() != null) {
            registry.registerTools(result.getTools());
        }
        // 注册全局技能
        if (result.getSkills() != null) {
            registry.registerSkills(result.getSkills());
        }
        WorkflowSpec workflow = result.getFirstWorkflow();
        registry.registerWorkflow(workflow);
        return workflow;
    }

    @Nested
    @DisplayName("execute 纯代码执行")
    class ExecuteCodeBlock {

        @Test
        @DisplayName("execute 闭包返回字符串结果")
        void shouldExecuteCodeBlockAndReturnResult() {
            String dsl = """
                        workflow("exec-test") {
                            steps {
                                step("compute") {
                                    execute { ctx ->
                                        return "hello-world"
                                    }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "input");

            assertEquals("hello-world", result.getFinalOutputAsString());
            assertEquals("hello-world", result.getStepResult("compute"));
            verifyNoInteractions(agentExecutor);
        }

        @Test
        @DisplayName("execute 闭包可访问上下文 lastOutput")
        void shouldAccessLastOutputInExecuteClosure() {
            String dsl = """
                        workflow("ctx-test") {
                            steps {
                                step("transform") {
                                    execute { ctx ->
                                        return "processed:" + ctx.lastOutput
                                    }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "raw-data");

            assertEquals("processed:raw-data", result.getFinalOutputAsString());
            verifyNoInteractions(agentExecutor);
        }

        @Test
        @DisplayName("execute 闭包搭配 output 转换")
        void shouldApplyOutputTransformAfterExecute() {
            String dsl = """
                        workflow("exec-output-test") {
                            steps {
                                step("compute") {
                                    execute { ctx ->
                                        return "raw"
                                    }
                                    output { result -> "final:" + result }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "input");

            assertEquals("final:raw", result.getFinalOutputAsString());
        }

        @Test
        @DisplayName("execute 步骤间数据传递")
        void shouldPassDataBetweenExecuteSteps() {
            String dsl = """
                        workflow("chain-test") {
                            steps {
                                step("step1") {
                                    execute { ctx ->
                                        return "step1-output"
                                    }
                                }
                                step("step2") {
                                    execute { ctx ->
                                        return ctx.lastOutput + "+step2"
                                    }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "init");

            assertEquals("step1-output+step2", result.getFinalOutputAsString());
            assertEquals("step1-output", result.getStepResult("step1"));
        }
    }

    @Nested
    @DisplayName("tool 直接工具调用")
    class DirectToolCall {

        @Test
        @DisplayName("直接调用 DSL 定义的全局工具")
        void shouldCallGlobalToolDirectly() {
            String dsl = """
                        tool("string_upper") {
                            description "将字符串转为大写"
                            parameter {
                                name "text"
                                type "string"
                                description "输入文本"
                                required true
                            }
                            execute { params ->
                                return params.text.toUpperCase()
                            }
                        }

                        workflow("tool-test") {
                            steps {
                                step("uppercase") {
                                    tool "string_upper"
                                    input { lastOutput -> [text: lastOutput] }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "hello");

            assertEquals("HELLO", result.getFinalOutputAsString());
            verifyNoInteractions(agentExecutor);
        }

        @Test
        @DisplayName("tool 节点搭配 output 转换")
        void shouldApplyOutputTransformAfterToolCall() {
            String dsl = """
                        tool("concat") {
                            description "拼接字符串"
                            parameter {
                                name "a"
                                type "string"
                                description "a"
                                required true
                            }
                            parameter {
                                name "b"
                                type "string"
                                description "b"
                                required true
                            }
                            execute { params -> params.a + params.b }
                        }

                        workflow("tool-output-test") {
                            steps {
                                step("join") {
                                    tool "concat"
                                    input { prev -> [a: "hello", b: "world"] }
                                    output { result -> "result=" + result }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "input");

            assertEquals("result=helloworld", result.getFinalOutputAsString());
        }
    }

    @Nested
    @DisplayName("execute 闭包中调用 toolCall()")
    class ExecuteWithToolCall {

        @Test
        @DisplayName("execute 闭包内使用 toolCall 调用工具")
        void shouldCallToolFromExecuteClosure() {
            String dsl = """
                        tool("reverse_string") {
                            description "反转字符串"
                            parameter {
                                name "text"
                                type "string"
                                description "输入"
                                required true
                            }
                            execute { params -> params.text.reverse() }
                        }

                        workflow("exec-toolcall-test") {
                            steps {
                                step("process") {
                                    execute { ctx ->
                                        def reversed = ctx.toolCall("reverse_string", [text: "hello"])
                                        return "reversed:" + reversed
                                    }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "input");

            assertEquals("reversed:olleh", result.getFinalOutputAsString());
            verifyNoInteractions(agentExecutor);
        }
    }

    @Nested
    @DisplayName("skill 直接技能调用")
    class DirectSkillCall {

        @Test
        @DisplayName("直接调用 Logic Skill")
        void shouldCallLogicSkillDirectly() {
            String dsl = """
                        skill("formatter") {
                            type "logic"
                            description "格式化数据"
                            parameter {
                                name "data"
                                type "string"
                                description "数据"
                                required true
                            }
                            parameter {
                                name "format"
                                type "string"
                                description "格式"
                                required true
                            }
                            execute { params ->
                                return "[" + params.format + "] " + params.data
                            }
                        }

                        workflow("skill-test") {
                            steps {
                                step("format") {
                                    skill "formatter"
                                    input { prev -> [data: prev, format: "JSON"] }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "test-data");

            assertEquals("[JSON] test-data", result.getFinalOutputAsString());
            verifyNoInteractions(agentExecutor);
        }
    }

    @Nested
    @DisplayName("混合编排：execute + agent")
    class HybridOrchestration {

        @Test
        @DisplayName("execute 准备数据 → agent 分析 → execute 后处理")
        void shouldMixExecuteAndAgentSteps() {
            String dsl = """
                        workflow("hybrid-test") {
                            steps {
                                step("prepare") {
                                    execute { ctx ->
                                        return "prepared:" + ctx.lastOutput
                                    }
                                }
                                step("analyze") {
                                    agent "analyst"
                                    input { data -> "分析: " + data }
                                }
                                step("postprocess") {
                                    execute { ctx ->
                                        return "final:" + ctx.lastOutput
                                    }
                                }
                            }
                        }
                    """;

            when(agentExecutor.chat(eq("analyst"), eq("分析: prepared:raw-input")))
                    .thenReturn("analysis-result");

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "raw-input");

            assertEquals("final:analysis-result", result.getFinalOutputAsString());
            assertEquals("prepared:raw-input", result.getStepResult("prepare"));
            assertEquals("analysis-result", result.getStepResult("analyze"));

            verify(agentExecutor, times(1)).chat(anyString(), anyString());
        }

        @Test
        @DisplayName("条件分支中使用 execute 模式")
        void shouldUseExecuteInConditionBranch() {
            String dsl = """
                        workflow("condition-exec") {
                            steps {
                                step("classify") {
                                    execute { ctx ->
                                        return ctx.lastOutput == "vip" ? "premium" : "standard"
                                    }
                                }
                                condition {
                                    check { result -> result }
                                    on("premium") {
                                        step("premium-handle") {
                                            execute { ctx -> return "VIP-处理完成" }
                                        }
                                    }
                                    on("standard") {
                                        step("standard-handle") {
                                            execute { ctx -> return "普通-处理完成" }
                                        }
                                    }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "vip");

            assertEquals("VIP-处理完成", result.getFinalOutputAsString());
            verifyNoInteractions(agentExecutor);
        }

        @Test
        @DisplayName("循环中使用 execute 模式")
        void shouldUseExecuteInLoop() {
            String dsl = """
                        workflow("loop-exec") {
                            steps {
                                step("init") {
                                    execute { ctx -> return "0" }
                                }
                                loop(maxIterations: 5) {
                                    step("increment") {
                                        execute { ctx ->
                                            int val = Integer.parseInt(ctx.lastOutput.toString())
                                            return String.valueOf(val + 1)
                                        }
                                    }
                                    until { result -> result == "3" }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "start");

            assertEquals("3", result.getFinalOutputAsString());
            verifyNoInteractions(agentExecutor);
        }
    }

    @Nested
    @DisplayName("execute 步骤可访问 stepResults")
    class StepResultAccess {

        @Test
        @DisplayName("execute 闭包可按名称访问之前步骤的结果")
        void shouldAccessPreviousStepResultByName() {
            String dsl = """
                        workflow("step-results-test") {
                            steps {
                                step("first") {
                                    execute { ctx -> return "A" }
                                }
                                step("second") {
                                    execute { ctx -> return "B" }
                                }
                                step("combine") {
                                    execute { ctx ->
                                        def a = ctx.getStepResult("first")
                                        def b = ctx.getStepResult("second")
                                        return a + "+" + b
                                    }
                                }
                            }
                        }
                    """;

            WorkflowSpec workflow = compileAndRegisterWorkflow(dsl);
            WorkflowResult result = workflowExecutor.execute(workflow, "input");

            assertEquals("A+B", result.getFinalOutputAsString());
        }
    }
}
