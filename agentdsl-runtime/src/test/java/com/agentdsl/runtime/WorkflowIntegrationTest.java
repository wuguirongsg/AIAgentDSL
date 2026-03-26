package com.agentdsl.runtime;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作流示例脚本集成测试。
 *
 * 默认 @Disabled，需要真实 API Key 才能运行。
 * 去掉 @Disabled 注解后，设置以下环境变量运行：
 *
 * OPENAI_API_KEY=sk-... 或 OLLAMA_BASE_URL=http://localhost:11434
 *
 * 运行命令：
 * ./gradlew :agentdsl-runtime:test --tests "*.WorkflowIntegrationTest"
 */
// 获取真实API_KEY方可运行
@Disabled("需要真实 LLM API Key，手动去掉此注解后运行")
class WorkflowIntegrationTest {

    private static final Path EXAMPLE_SCRIPT = Paths.get("../examples/workflow-demo.agent.groovy");

    /**
     * 最简单的冒烟测试：只跑一个 Agent 一次调用。
     * 先用这个确认 Ollama 连接通畅，再跑复杂工作流。
     */
    @Test
    void smokeTest_singleAgentCall() throws Exception {
        AgentDslEngine engine = new AgentDslEngine();

        // 用内联 DSL 而不是文件，避免路径问题
        engine.load("""
                    workflow("smoke") {
                        steps {
                            step("translate") {
                                agent "translator"
                                input { text -> "翻译成英文，只输出翻译结果：" + text }
                            }
                        }
                    }

                    agent("translator") {
                        model {
                            provider "ollama"
                            modelName "qwen:0.5b-chat"
                        }
                        systemPrompt "你是翻译员，只输出翻译结果。"
                    }
                """);

        System.out.println("[smoke] 开始调用 Ollama qwen3:4b ...");
        long start = System.currentTimeMillis();

        WorkflowResult result = engine.executeWorkflow("smoke", "你好世界");

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[smoke] 完成，耗时: " + elapsed + "ms");
        System.out.println("[smoke] 结果: " + result.getFinalOutputAsString());

        assertNotNull(result.getFinalOutputAsString());
    }

    @Test
    void shouldRunTranslatePipeline() throws Exception {
        AgentDslEngine engine = new AgentDslEngine();
        engine.loadFile(EXAMPLE_SCRIPT);

        WorkflowResult result = engine.executeWorkflow(
                "translate-pipeline",
                "人工智能正在改变世界。");

        assertNotNull(result);
        System.out.println("=== translate-pipeline 结果 ===");
        System.out.println(result.getFinalOutputAsString());
        System.out.println("所有步骤结果: " + result.getStepResults().keySet());
    }

    @Test
    void shouldRunMultiAnalysis() throws Exception {
        AgentDslEngine engine = new AgentDslEngine();
        engine.loadFile(EXAMPLE_SCRIPT);

        WorkflowResult result = engine.executeWorkflow(
                "multi-analysis",
                "AgentDSL is an amazing tool for building AI agent pipelines!");

        assertNotNull(result);
        System.out.println("=== multi-analysis 结果 ===");
        System.out.println("情感分析: " + result.getStepResult("sentiment"));
        System.out.println("关键词: " + result.getStepResult("keywords"));
        System.out.println("最终报告: " + result.getFinalOutputAsString());
    }

    @Test
    void shouldRunQualityAwareFormat() throws Exception {
        AgentDslEngine engine = new AgentDslEngine();
        engine.loadFile(EXAMPLE_SCRIPT);

        WorkflowResult result = engine.executeWorkflow(
                "quality-aware-format",
                "AI is revolutionizing the way we build software.");

        assertNotNull(result);
        System.out.println("=== quality-aware-format 结果 ===");
        System.out.println(result.getFinalOutputAsString());
    }
}
