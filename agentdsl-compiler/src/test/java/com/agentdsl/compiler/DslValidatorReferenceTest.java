package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ModelSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DslValidator 引用存在性校验测试（Sprint 5 新增）。
 */
class DslValidatorReferenceTest {

    // -----------------------------------------------------------------------
    // 工具引用校验
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("工具引用存在性校验")
    class ToolReferenceValidation {

        @Test
        @DisplayName("引用已定义工具 — 通过")
        void shouldPassWhenToolExists() {
            AgentSpec agent = agentWithRefs("my-agent", List.of("my-tool"));
            ToolSpec tool = toolSpec("my-tool");
            // 不抛异常
            assertDoesNotThrow(() -> DslValidator.validateToolReferences(
                    List.of(agent), List.of(tool)));
        }

        @Test
        @DisplayName("引用未定义工具 — 抛出 ADSL-003")
        void shouldFailWhenToolNotDefined() {
            AgentSpec agent = agentWithRefs("my-agent", List.of("nonexistent-tool"));
            DslCompilationException ex = assertThrows(DslCompilationException.class,
                    () -> DslValidator.validateToolReferences(List.of(agent), List.of()));
            assertEquals("ADSL-003", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("nonexistent-tool"));
        }

        @Test
        @DisplayName("Agent 无引用 — 通过")
        void shouldPassWhenAgentHasNoRefs() {
            AgentSpec agent = agentWithRefs("my-agent", null);
            assertDoesNotThrow(() -> DslValidator.validateToolReferences(
                    List.of(agent), List.of()));
        }

        @Test
        @DisplayName("引用多工具，部分未定义 — 抛出异常并指明缺失工具名")
        void shouldIndicateMissingToolName() {
            AgentSpec agent = agentWithRefs("my-agent", List.of("tool-a", "missing-b"));
            ToolSpec toolA = toolSpec("tool-a");
            DslCompilationException ex = assertThrows(DslCompilationException.class,
                    () -> DslValidator.validateToolReferences(
                            List.of(agent), List.of(toolA)));
            assertTrue(ex.getMessage().contains("missing-b"));
        }
    }

    // -----------------------------------------------------------------------
    // 工作流 Agent 引用校验
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("工作流 Agent 引用校验")
    class WorkflowAgentReferenceValidation {

        @Test
        @DisplayName("通过 DslCompiler 验证工作流正常编译")
        void shouldPassWithDslCompilerWhenAgentsDefined() {
            // 工作流与 Agent 在同一脚本中定义，不会触发引用校验错误
            String dsl = """
                    agent("translator") {
                        model { provider "ollama"; modelName "qwen:0.5b-chat" }
                    }
                    workflow("translate-wf") {
                        steps {
                            step("translate") {
                                agent "translator"
                            }
                        }
                    }
                    """;
            DslCompiler compiler = new DslCompiler();
            assertDoesNotThrow(() -> compiler.compile(dsl));
        }

        @Test
        @DisplayName("直接调用校验 — 引用已定义 Agent 通过")
        void shouldPassWhenAgentExists() {
            AgentSpec agent = agentSpec("my-agent");
            WorkflowSpec workflow = workflowWithSingleStep("my-wf", "my-agent");
            assertDoesNotThrow(() -> DslValidator.validateWorkflowAgentReferences(
                    List.of(workflow), List.of(agent)));
        }

        @Test
        @DisplayName("直接调用校验 — 引用未定义 Agent 抛出 ADSL-003")
        void shouldFailWhenAgentNotDefined() {
            WorkflowSpec workflow = workflowWithSingleStep("my-wf", "ghost-agent");
            DslCompilationException ex = assertThrows(DslCompilationException.class,
                    () -> DslValidator.validateWorkflowAgentReferences(
                            List.of(workflow), List.of()));
            assertEquals("ADSL-003", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("ghost-agent"));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AgentSpec agentSpec(String name) {
        AgentSpec spec = new AgentSpec();
        spec.setName(name);
        ModelSpec model = new ModelSpec();
        model.setProvider("ollama");
        model.setModelName("qwen2.5");
        spec.setModel(model);
        return spec;
    }

    private AgentSpec agentWithRefs(String name, List<String> refs) {
        AgentSpec spec = agentSpec(name);
        spec.setToolRefs(refs);
        return spec;
    }

    private ToolSpec toolSpec(String name) {
        ToolSpec t = new ToolSpec();
        t.setName(name);
        t.setDescription("test tool");
        t.setExecuteBody(new groovy.lang.Closure<>(null) {
        });
        return t;
    }

    private WorkflowSpec workflowWithSingleStep(String workflowName, String agentRef) {
        String dsl = """
                workflow("%s") {
                    steps {
                        step("step1") {
                            agent "%s"
                        }
                    }
                }
                """.formatted(workflowName, agentRef);
        DslCompiler compiler = new DslCompiler();
        // 仅解析（不触发引用校验，因为没有 Agent 定义）
        try {
            return compiler.compile(dsl).getFirstWorkflow();
        } catch (Exception e) {
            // 直接构建 WorkflowSpec
            com.agentdsl.core.spec.StepSpec step = new com.agentdsl.core.spec.StepSpec();
            step.setName("step1");
            step.setAgentRef(agentRef);
            step.setType(com.agentdsl.core.spec.StepSpec.StepType.SEQUENTIAL);
            WorkflowSpec ws = new WorkflowSpec();
            ws.setName(workflowName);
            ws.setSteps(List.of(step));
            return ws;
        }
    }
}
