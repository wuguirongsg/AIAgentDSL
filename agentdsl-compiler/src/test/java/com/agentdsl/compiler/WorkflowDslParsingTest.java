package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.StepSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作流 DSL 解析测试。
 * 验证 workflow() 语法的各种形式可正确解析为 WorkflowSpec。
 */
class WorkflowDslParsingTest {

    private DslCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DslCompiler();
    }

    @Nested
    @DisplayName("顺序执行 step 解析")
    class SequentialStepParsing {

        @Test
        @DisplayName("基础顺序工作流")
        void shouldParseSequentialWorkflow() {
            String dsl = """
                        workflow("translate-pipeline") {
                            description "翻译流水线"

                            steps {
                                step("translate") {
                                    agent "translator"
                                    input { text -> text }
                                }

                                step("review") {
                                    agent "reviewer"
                                    input { translated -> "审查: ${translated}" }
                                }

                                step("polish") {
                                    agent "polisher"
                                    output { result -> result }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(0, result.getAgents().size());
            assertEquals(1, result.getWorkflows().size());

            WorkflowSpec workflow = result.getFirstWorkflow();
            assertEquals("translate-pipeline", workflow.getName());
            assertEquals("翻译流水线", workflow.getDescription());
            assertEquals(3, workflow.getSteps().size());

            StepSpec step1 = workflow.getSteps().get(0);
            assertEquals(StepSpec.StepType.SEQUENTIAL, step1.getType());
            assertEquals("translate", step1.getName());
            assertEquals("translator", step1.getAgentRef());
            assertNotNull(step1.getInputTransform());

            StepSpec step2 = workflow.getSteps().get(1);
            assertEquals("review", step2.getName());
            assertEquals("reviewer", step2.getAgentRef());

            StepSpec step3 = workflow.getSteps().get(2);
            assertEquals("polish", step3.getName());
            assertEquals("polisher", step3.getAgentRef());
            assertNotNull(step3.getOutputTransform());
        }
    }

    @Nested
    @DisplayName("并行 parallel 解析")
    class ParallelStepParsing {

        @Test
        @DisplayName("并行步骤")
        void shouldParseParallelSteps() {
            String dsl = """
                        workflow("multi-analysis") {
                            description "多维度分析"

                            steps {
                                parallel {
                                    step("sentiment") {
                                        agent "sentiment-analyzer"
                                    }
                                    step("summary") {
                                        agent "summarizer"
                                    }
                                    step("keywords") {
                                        agent "keyword-extractor"
                                    }
                                }

                                step("combine") {
                                    agent "report-generator"
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            WorkflowSpec workflow = result.getFirstWorkflow();

            assertEquals(2, workflow.getSteps().size());

            // 第一个是 parallel
            StepSpec parallel = workflow.getSteps().get(0);
            assertEquals(StepSpec.StepType.PARALLEL, parallel.getType());
            assertEquals(3, parallel.getParallelSteps().size());
            assertEquals("sentiment", parallel.getParallelSteps().get(0).getName());
            assertEquals("summarizer", parallel.getParallelSteps().get(1).getAgentRef());
            assertEquals("keyword-extractor", parallel.getParallelSteps().get(2).getAgentRef());

            // 第二个是顺序
            StepSpec combine = workflow.getSteps().get(1);
            assertEquals(StepSpec.StepType.SEQUENTIAL, combine.getType());
            assertEquals("combine", combine.getName());
        }
    }

    @Nested
    @DisplayName("条件 condition 解析")
    class ConditionStepParsing {

        @Test
        @DisplayName("条件路由")
        void shouldParseConditionStep() {
            String dsl = """
                        workflow("support-pipeline") {
                            description "工单处理"

                            steps {
                                step("classify") {
                                    agent "classifier"
                                }

                                condition {
                                    check { result -> result }

                                    on("L1") {
                                        step("handle") {
                                            agent "support-agent"
                                        }
                                    }

                                    on("L2") {
                                        step("escalate") {
                                            agent "escalation-agent"
                                        }
                                        step("notify") {
                                            agent "notification-agent"
                                        }
                                    }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            WorkflowSpec workflow = result.getFirstWorkflow();

            assertEquals(2, workflow.getSteps().size());

            StepSpec condition = workflow.getSteps().get(1);
            assertEquals(StepSpec.StepType.CONDITION, condition.getType());
            assertNotNull(condition.getCheckClosure());

            // 验证分支
            assertEquals(2, condition.getBranches().size());
            assertTrue(condition.getBranches().containsKey("L1"));
            assertTrue(condition.getBranches().containsKey("L2"));

            List<StepSpec> l1Steps = condition.getBranches().get("L1");
            assertEquals(1, l1Steps.size());
            assertEquals("handle", l1Steps.get(0).getName());
            assertEquals("support-agent", l1Steps.get(0).getAgentRef());

            List<StepSpec> l2Steps = condition.getBranches().get("L2");
            assertEquals(2, l2Steps.size());
            assertEquals("escalate", l2Steps.get(0).getName());
            assertEquals("notify", l2Steps.get(1).getName());
        }
    }

    @Nested
    @DisplayName("循环 loop 解析")
    class LoopStepParsing {

        @Test
        @DisplayName("循环迭代")
        void shouldParseLoopStep() {
            String dsl = """
                        workflow("refine-article") {
                            description "文章打磨"

                            steps {
                                step("draft") {
                                    agent "writer"
                                    input { topic -> "撰写: ${topic}" }
                                }

                                loop(maxIterations: 3) {
                                    step("review") {
                                        agent "reviewer"
                                    }

                                    until { result -> result == "pass" }

                                    step("revise") {
                                        agent "writer"
                                        input { review -> "修改: ${review}" }
                                    }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            WorkflowSpec workflow = result.getFirstWorkflow();

            assertEquals(2, workflow.getSteps().size());

            StepSpec loop = workflow.getSteps().get(1);
            assertEquals(StepSpec.StepType.LOOP, loop.getType());
            assertEquals(3, loop.getMaxIterations());
            assertNotNull(loop.getUntilClosure());

            // 循环体
            assertEquals(2, loop.getLoopBody().size());
            assertEquals("review", loop.getLoopBody().get(0).getName());
            assertEquals("reviewer", loop.getLoopBody().get(0).getAgentRef());
            assertEquals("revise", loop.getLoopBody().get(1).getName());
            assertEquals("writer", loop.getLoopBody().get(1).getAgentRef());
        }
    }

    @Nested
    @DisplayName("混合定义")
    class MixedDefinitions {

        @Test
        @DisplayName("Agent + Workflow 混合定义")
        void shouldParseMixedAgentAndWorkflow() {
            String dsl = """
                        agent("translator") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }
                            systemPrompt "你是翻译员"
                        }

                        agent("reviewer") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                        }

                        workflow("translate-pipeline") {
                            steps {
                                step("translate") {
                                    agent "translator"
                                }
                                step("review") {
                                    agent "reviewer"
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(2, result.getAgents().size());
            assertEquals(1, result.getWorkflows().size());
            assertEquals("translate-pipeline", result.getFirstWorkflow().getName());
            assertEquals(2, result.getFirstWorkflow().getSteps().size());
        }
    }

    @Nested
    @DisplayName("校验与错误处理")
    class ValidationAndErrors {

        @Test
        @DisplayName("Workflow 缺少 steps 应抛出异常")
        void shouldThrowWhenStepsMissing() {
            String dsl = """
                        workflow("empty") {
                            description "没有步骤的工作流"
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("steps"));
        }
    }
}
