package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ToolSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DslCompiler 单元测试。
 * 验证 DSL 脚本可正确解析为 AgentSpec。
 */
class DslCompilerTest {

    private DslCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DslCompiler();
    }

    @Nested
    @DisplayName("基础 Agent 解析")
    class BasicAgentParsing {

        @Test
        @DisplayName("最小化 Agent 定义")
        void shouldParseMinimalAgent() {
            String dsl = """
                        agent("greeter") {
                            model {
                                provider "ollama"
                                modelName "qwen2.5"
                            }
                            systemPrompt "你好"
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertNotNull(result);
            assertEquals(1, result.getAgents().size());

            AgentSpec agent = result.getFirstAgent();
            assertEquals("greeter", agent.getName());
            assertEquals("ollama", agent.getModel().getProvider());
            assertEquals("qwen2.5", agent.getModel().getModelName());
            assertEquals("你好", agent.getSystemPrompt());
        }

        @Test
        @DisplayName("完整 Agent 定义（model + memory + description）")
        void shouldParseFullAgent() {
            String dsl = """
                        agent("assistant") {
                            description "测试助手"

                            model {
                                provider "openai"
                                modelName "gpt-4"
                                temperature 0.3
                                maxTokens 4000
                                timeout 120
                            }

                            systemPrompt "你是一个助手。"

                            memory {
                                type "message_window"
                                maxMessages 30
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertEquals("assistant", agent.getName());
            assertEquals("测试助手", agent.getDescription());
            assertEquals("openai", agent.getModel().getProvider());
            assertEquals("gpt-4", agent.getModel().getModelName());
            assertEquals(0.3, agent.getModel().getTemperature());
            assertEquals(4000, agent.getModel().getMaxTokens());
            assertEquals(120, agent.getModel().getTimeout());
            assertEquals("message_window", agent.getMemory().getType());
            assertEquals(30, agent.getMemory().getMaxMessages());
        }

        @Test
        @DisplayName("Agent 默认值")
        void shouldApplyDefaults() {
            String dsl = """
                        agent("defaults") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            // Model defaults
            assertEquals(0.7, agent.getModel().getTemperature());
            assertEquals(1.0, agent.getModel().getTopP());
            assertEquals(2048, agent.getModel().getMaxTokens());
            assertEquals(60, agent.getModel().getTimeout());

            // Memory should be null if not defined
            assertNull(agent.getMemory());
        }
    }

    @Nested
    @DisplayName("工具解析")
    class ToolParsing {

        @Test
        @DisplayName("Agent 内联工具定义")
        void shouldParseInlineTools() {
            String dsl = """
                        agent("tool-agent") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }

                            tools {
                                tool("getCurrentTime") {
                                    description "获取当前时间"
                                    execute { ->
                                        return java.time.LocalDateTime.now().toString()
                                    }
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertEquals(1, agent.getTools().size());
            assertEquals("getCurrentTime", agent.getTools().get(0).getName());
            assertEquals("获取当前时间", agent.getTools().get(0).getDescription());
            assertNotNull(agent.getTools().get(0).getExecuteBody());
        }

        @Test
        @DisplayName("Agent 工具引用")
        void shouldParseToolRefs() {
            String dsl = """
                        agent("ref-agent") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }

                            tools {
                                include "weatherQuery"
                                include "orderQuery"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertEquals(2, agent.getToolRefs().size());
            assertTrue(agent.getToolRefs().contains("weatherQuery"));
            assertTrue(agent.getToolRefs().contains("orderQuery"));
        }

        @Test
        @DisplayName("独立工具定义")
        void shouldParseStandaloneTool() {
            String dsl = """
                        tool("weather") {
                            description "查询天气"

                            parameter {
                                name "city"
                                type "string"
                                description "城市名"
                                required true
                            }

                            execute { params ->
                                return "晴天"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(0, result.getAgents().size());
            assertEquals(1, result.getTools().size());

            ToolSpec tool = result.getTools().get(0);
            assertEquals("weather", tool.getName());
            assertEquals("查询天气", tool.getDescription());
            assertEquals(1, tool.getParameters().size());
            assertEquals("city", tool.getParameters().get(0).getName());
            assertEquals("string", tool.getParameters().get(0).getType());
            assertTrue(tool.getParameters().get(0).isRequired());
        }

        @Test
        @DisplayName("工具带多个参数")
        void shouldParseToolWithMultipleParameters() {
            String dsl = """
                        tool("search") {
                            description "搜索"

                            parameter {
                                name "query"
                                type "string"
                                description "搜索关键词"
                                required true
                            }

                            parameter {
                                name "limit"
                                type "integer"
                                description "最大结果数"
                                required false
                            }

                            execute { params ->
                                return "results"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            ToolSpec tool = result.getTools().get(0);

            assertEquals(2, tool.getParameters().size());
            assertEquals("query", tool.getParameters().get(0).getName());
            assertEquals("limit", tool.getParameters().get(1).getName());
            assertEquals("integer", tool.getParameters().get(1).getType());
        }
    }

    @Nested
    @DisplayName("高级特性解析")
    class AdvancedFeatures {

        @Test
        @DisplayName("RAG 配置")
        void shouldParseRagConfig() {
            String dsl = """
                        agent("rag-agent") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }

                            rag {
                                contentRetriever {
                                    type "embedding_store"
                                    embeddingModel "text-embedding-3-small"
                                    maxResults 10
                                    minScore 0.8
                                }
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertNotNull(agent.getRag());
            assertNotNull(agent.getRag().getContentRetriever());
            assertEquals("embedding_store", agent.getRag().getContentRetriever().getType());
            assertEquals("text-embedding-3-small", agent.getRag().getContentRetriever().getEmbeddingModel());
            assertEquals(10, agent.getRag().getContentRetriever().getMaxResults());
            assertEquals(0.8, agent.getRag().getContentRetriever().getMinScore());
        }

        @Test
        @DisplayName("Guardrails 配置")
        void shouldParseGuardrails() {
            String dsl = """
                        agent("safe-agent") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }

                            guardrails {
                                maxTokensPerRequest 5000
                                blockedTopics "politics", "violence"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertNotNull(agent.getGuardrails());
            assertEquals(5000, agent.getGuardrails().getMaxTokensPerRequest());
            assertEquals(2, agent.getGuardrails().getBlockedTopics().size());
            assertTrue(agent.getGuardrails().getBlockedTopics().contains("politics"));
        }

        @Test
        @DisplayName("OutputSchema 结构化输出")
        void shouldParseOutputSchema() {
            String dsl = """
                        agent("analyzer") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }

                            outputSchema {
                                field "sentiment", "string", "情感类型"
                                field "confidence", "double", "置信度"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            AgentSpec agent = result.getFirstAgent();

            assertNotNull(agent.getOutputSchema());
            assertEquals(2, agent.getOutputSchema().getFields().size());
            assertEquals("sentiment", agent.getOutputSchema().getFields().get(0).getName());
            assertEquals("string", agent.getOutputSchema().getFields().get(0).getType());
            assertEquals("confidence", agent.getOutputSchema().getFields().get(1).getName());
        }

        @Test
        @DisplayName("多个 Agent 定义在同一脚本")
        void shouldParseMultipleAgents() {
            String dsl = """
                        agent("agent-a") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }
                        }

                        agent("agent-b") {
                            model {
                                provider "ollama"
                                modelName "qwen2.5"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(2, result.getAgents().size());
            assertEquals("agent-a", result.getAgents().get(0).getName());
            assertEquals("agent-b", result.getAgents().get(1).getName());
        }

        @Test
        @DisplayName("Agent 和独立 Tool 混合定义")
        void shouldParseMixedDefinitions() {
            String dsl = """
                        tool("helper") {
                            description "辅助工具"
                            execute { -> "ok" }
                        }

                        agent("main") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }
                            tools {
                                include "helper"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(1, result.getAgents().size());
            assertEquals(1, result.getTools().size());
            assertEquals("helper", result.getTools().get(0).getName());
            assertTrue(result.getAgents().get(0).getToolRefs().contains("helper"));
        }
    }

    @Nested
    @DisplayName("校验与错误处理")
    class ValidationAndErrors {

        @Test
        @DisplayName("缺少 model 块应抛出异常")
        void shouldThrowWhenModelMissing() {
            String dsl = """
                        agent("no-model") {
                            systemPrompt "hello"
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("model"));
        }

        @Test
        @DisplayName("缺少 provider 应抛出异常")
        void shouldThrowWhenProviderMissing() {
            String dsl = """
                        agent("no-provider") {
                            model {
                                modelName "gpt-4"
                            }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("provider"));
        }

        @Test
        @DisplayName("缺少 modelName 应抛出异常")
        void shouldThrowWhenModelNameMissing() {
            String dsl = """
                        agent("no-modelname") {
                            model {
                                provider "openai"
                            }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("modelName"));
        }

        @Test
        @DisplayName("Tool 缺少 description 应抛出异常")
        void shouldThrowWhenToolDescriptionMissing() {
            String dsl = """
                        tool("bad-tool") {
                            execute { -> "ok" }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("description"));
        }

        @Test
        @DisplayName("Tool 缺少 execute 应抛出异常")
        void shouldThrowWhenToolExecuteMissing() {
            String dsl = """
                        tool("no-exec") {
                            description "没有执行体的工具"
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("execute"));
        }
    }
}
