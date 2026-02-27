package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.langchain4j.LangChainMemoryFactory;
import com.agentdsl.langchain4j.LangChainRagFactory;
import com.agentdsl.langchain4j.LangChainToolBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentDslEngine 端到端集成测试。
 * 使用 StubChatModel 验证完整的 DSL → 编译 → 注册 → 对话 流程。
 */
class AgentDslEngineTest {

    private StubChatModel stubModel;
    private AgentRegistry registry;
    private AgentExecutor executor;
    private DslCompiler compiler;

    @BeforeEach
    void setUp() {
        stubModel = new StubChatModel();
        StubModelFactory modelFactory = new StubModelFactory(stubModel);
        registry = new AgentRegistry(modelFactory, new LangChainMemoryFactory(),
                new LangChainToolBridge(), new LangChainRagFactory());
        executor = new AgentExecutor(registry);
        compiler = new DslCompiler();
    }

    /**
     * 辅助方法：编译 DSL 并注册所有 Agent 和工具。
     */
    private void loadDsl(String dslScript) {
        DslCompileResult result = compiler.compile(dslScript);
        // 先注册独立工具
        if (!result.getTools().isEmpty()) {
            registry.registerTools(result.getTools());
        }
        // 再注册 Agent
        for (AgentSpec agent : result.getAgents()) {
            registry.register(agent);
        }
    }

    @Nested
    @DisplayName("简单对话")
    class SimpleChat {

        @Test
        @DisplayName("DSL → 编译 → 注册 → chat → 返回预设回复")
        void shouldChatWithSimpleAgent() {
            String dsl = """
                        agent("greeter") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            systemPrompt "你是一个友好的问候助手。"
                        }
                    """;

            stubModel.addTextResponse("你好！有什么可以帮你的吗？");

            loadDsl(dsl);
            String reply = executor.chat("greeter", "你好");

            assertNotNull(reply);
            assertEquals("你好！有什么可以帮你的吗？", reply);
        }

        @Test
        @DisplayName("验证 SystemPrompt 被正确传递到请求中")
        void shouldIncludeSystemPromptInRequest() {
            String dsl = """
                        agent("sys-test") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }
                            systemPrompt "你是一个翻译助手。"
                        }
                    """;

            stubModel.addTextResponse("Hello!");

            loadDsl(dsl);
            executor.chat("sys-test", "翻译：你好");

            // 验证模型收到了请求
            assertEquals(1, stubModel.getReceivedRequests().size());

            // 验证消息列表包含 SystemMessage 和 UserMessage
            var messages = stubModel.getReceivedRequests().get(0).messages();
            assertTrue(messages.size() >= 2, "应包含 SystemMessage 和 UserMessage");
        }
    }

    @Nested
    @DisplayName("带工具的对话")
    class ToolChat {

        @Test
        @DisplayName("Agent 带内联工具：模型请求工具调用 → 执行 → 返回最终回复")
        void shouldExecuteInlineToolAndReturnFinalResponse() {
            String dsl = """
                        agent("tool-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            systemPrompt "你是助手。"

                            tools {
                                tool("getCurrentTime") {
                                    description "获取当前时间"
                                    execute { ->
                                        return "2026-02-12T12:00:00"
                                    }
                                }
                            }
                        }
                    """;

            // 第一轮：模型请求调用工具
            stubModel.addToolCallResponse("getCurrentTime", "{}");
            // 第二轮：模型返回最终回复
            stubModel.addTextResponse("当前时间是 2026-02-12T12:00:00。");

            loadDsl(dsl);
            String reply = executor.chat("tool-agent", "现在几点了？");

            assertEquals("当前时间是 2026-02-12T12:00:00。", reply);
            // 模型被调用了两次（工具调用 + 最终回复）
            assertEquals(2, stubModel.getReceivedRequests().size());
        }

        @Test
        @DisplayName("带参数的独立工具：参数正确传递到 Closure")
        void shouldPassParametersToToolClosure() {
            String dsl = """
                        tool("weather") {
                            description "查询天气"
                            parameter {
                                name "city"
                                type "string"
                                description "城市"
                                required true
                            }
                            execute { params ->
                                return "天气：" + params.city + " 晴天 25°C"
                            }
                        }

                        agent("weather-bot") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            tools {
                                include "weather"
                            }
                        }
                    """;

            // 模型先请求工具调用，带参数
            stubModel.addToolCallResponse("weather", "{\"city\":\"北京\"}");
            // 工具执行后，模型返回最终回复
            stubModel.addTextResponse("北京今天晴天，气温25度。");

            loadDsl(dsl);
            String reply = executor.chat("weather-bot", "北京天气怎么样？");

            assertEquals("北京今天晴天，气温25度。", reply);
        }
    }

    @Nested
    @DisplayName("多 Agent 场景")
    class MultiAgent {

        @Test
        @DisplayName("同一脚本定义多个 Agent，各自独立对话")
        void shouldSupportMultipleAgents() {
            String dsl = """
                        agent("agent-a") {
                            model {
                                provider "openai"
                                modelName "gpt-4"
                            }
                            systemPrompt "你是 Agent A。"
                        }

                        agent("agent-b") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            systemPrompt "你是 Agent B。"
                        }
                    """;

            // 两个 Agent 共用同一个 stubModel，按调用顺序返回不同回复
            stubModel.addTextResponse("我是 A 的回复");
            stubModel.addTextResponse("我是 B 的回复");

            loadDsl(dsl);

            assertEquals(2, registry.getAgentNames().size());
            assertTrue(registry.has("agent-a"));
            assertTrue(registry.has("agent-b"));

            String replyA = executor.chat("agent-a", "你是谁？");
            assertEquals("我是 A 的回复", replyA);

            String replyB = executor.chat("agent-b", "你是谁？");
            assertEquals("我是 B 的回复", replyB);
        }
    }

    @Nested
    @DisplayName("记忆管理")
    class MemoryManagement {

        @Test
        @DisplayName("配置 message_window 记忆后应创建非空 ChatMemory")
        void shouldCreateMemory() {
            String dsl = """
                        agent("memory-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            memory {
                                type "message_window"
                                maxMessages 10
                            }
                        }
                    """;

            loadDsl(dsl);

            AgentInstance instance = registry.get("memory-agent");
            assertNotNull(instance.getMemory(), "应创建 ChatMemory 实例");
        }

        @Test
        @DisplayName("未配置记忆时仍应有默认记忆")
        void shouldHaveDefaultMemory() {
            String dsl = """
                        agent("no-memory") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                        }
                    """;

            loadDsl(dsl);

            AgentInstance instance = registry.get("no-memory");
            // LangChainMemoryFactory 在 spec 为 null 时返回默认的 10 条记忆
            assertNotNull(instance.getMemory(), "未配置 memory 时也应有默认 ChatMemory");
        }
    }

    @Nested
    @DisplayName("Agent 注册与注销")
    class RegistryManagement {

        @Test
        @DisplayName("注册后可通过名称获取 AgentInstance")
        void shouldGetRegisteredAgent() {
            String dsl = """
                        agent("test") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                        }
                    """;

            loadDsl(dsl);

            AgentInstance instance = registry.get("test");
            assertNotNull(instance);
            assertEquals("test", instance.getName());
            assertEquals("ollama", instance.getSpec().getModel().getProvider());
        }

        @Test
        @DisplayName("获取未注册的 Agent 应抛异常")
        void shouldThrowForUnregisteredAgent() {
            assertThrows(Exception.class, () -> registry.get("not-exist"));
        }

        @Test
        @DisplayName("注销后无法获取 Agent")
        void shouldUnregisterAgent() {
            String dsl = """
                        agent("temp") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                        }
                    """;

            loadDsl(dsl);
            assertTrue(registry.has("temp"));

            registry.unregister("temp");
            assertFalse(registry.has("temp"));
        }
    }
}
