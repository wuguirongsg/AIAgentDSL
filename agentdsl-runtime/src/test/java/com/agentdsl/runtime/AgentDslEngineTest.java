package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.langchain4j.LangChainMemoryFactory;
import com.agentdsl.langchain4j.LangChainRagFactory;
import com.agentdsl.langchain4j.LangChainToolBridge;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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

        @Test
        @DisplayName("hypergraph memory 应暴露 deep_recall 工具并参与对话")
        void shouldExposeDeepRecallCapability() throws Exception {
            Path tempDir = Files.createTempDirectory("runtime-hypergraph-memory");
            String dsl = """
                        agent("hyper-memory-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            memory {
                                type "hypergraph"
                                stmMaxMessages 2
                                sqliteJdbcUrl "%s"
                            }
                            skills {
                                include "deep_recall"
                            }
                        }
                    """.formatted("jdbc:sqlite:" + tempDir.resolve("memory.db"));

            loadDsl(dsl);

            AgentInstance instance = registry.get("hyper-memory-agent");
            assertNotNull(instance.getMemory());
            assertTrue(instance.getToolExecutors().containsKey("deep_recall"),
                    "声明 deep_recall skill 后应注册对应工具");

            instance.getMemory().add(UserMessage.from("我喜欢黑咖啡，不加糖。"));
            instance.getMemory().add(UserMessage.from("请记住我的咖啡偏好。"));
            instance.getMemory().add(UserMessage.from("之后回答时可以直接引用这个偏好。"));
            stubModel.addToolCallResponse("deep_recall", "{\"query\":\"咖啡偏好\"}");
            stubModel.addTextResponse("用户提到了其咖啡偏好，希望记住。");  // ChatModelMemoryReconstructor 重建调用
            stubModel.addTextResponse("你偏好黑咖啡，不加糖。");

            String reply = executor.chat("hyper-memory-agent", "我的咖啡偏好是什么？");
            assertEquals("你偏好黑咖啡，不加糖。", reply);
            assertTrue(stubModel.getReceivedRequests().size() >= 3, "应包含工具调用、记忆重建和最终回复");
        }

        @Test
        @DisplayName("hypergraph memory 命中 deep recall 时应调用真实模型重建")
        void shouldUseChatModelForDeepRecallReconstruction() throws Exception {
            Path tempDir = Files.createTempDirectory("runtime-hypergraph-memory-reconstruction");
            String dsl = """
                        agent("hyper-memory-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            memory {
                                type "hypergraph"
                                stm {
                                    maxEdges 2
                                }
                                deepRecallThreshold 0.4
                                ltm {
                                    backend "sqlite"
                                    path "%s"
                                }
                            }
                            skills {
                                include "deep_recall"
                            }
                        }
                    """.formatted(tempDir.resolve("memory.db"));

            loadDsl(dsl);

            AgentInstance instance = registry.get("hyper-memory-agent");
            instance.getMemory().add(UserMessage.from("我喜欢黑咖啡，不加糖。"));
            instance.getMemory().add(UserMessage.from("请记住我喜欢黑咖啡。"));
            instance.getMemory().add(UserMessage.from("之后回答时可以直接引用这个偏好。"));
            stubModel.addToolCallResponse("deep_recall", "{\"query\":\"黑咖啡\"}");
            stubModel.addTextResponse("根据长期记忆重建，用户偏好黑咖啡且不加糖。");
            stubModel.addTextResponse("你偏好黑咖啡，不加糖。");

            String reply = executor.chat("hyper-memory-agent", "我喜欢喝什么咖啡？");
            assertEquals("你偏好黑咖啡，不加糖。", reply);

            boolean foundReconstructionPrompt = stubModel.getReceivedRequests().stream()
                    .flatMap(request -> request.messages().stream())
                    .anyMatch(message -> message instanceof dev.langchain4j.data.message.UserMessage userMessage
                            && userMessage.singleText().contains("重建最相关的历史上下文"));

            assertTrue(foundReconstructionPrompt, "命中 deep recall 后应向模型发送记忆重建提示词");
        }

        @Test
        @DisplayName("hypergraph memory 配置 compressionModel 后应调用真实模型压缩")
        void shouldUseCompressionModelWhenConfigured() throws Exception {
            Path tempDir = Files.createTempDirectory("runtime-hypergraph-memory-compression");
            String dsl = """
                        agent("hyper-memory-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            memory {
                                type "hypergraph"
                                stm {
                                    maxEdges 1
                                }
                                ltm {
                                    backend "sqlite"
                                    path "%s"
                                    compressionModel "qwen3:4b"
                                }
                            }
                        }
                    """.formatted(tempDir.resolve("memory.db"));

            loadDsl(dsl);

            AgentInstance instance = registry.get("hyper-memory-agent");
            stubModel.addTextResponse("用户咖啡偏好摘要");
            instance.getMemory().add(UserMessage.from("我喜欢黑咖啡，不加糖。"));
            instance.getMemory().add(UserMessage.from("请记住我的咖啡偏好。"));

            boolean foundCompressionPrompt = stubModel.getReceivedRequests().stream()
                    .flatMap(request -> request.messages().stream())
                    .anyMatch(message -> message instanceof dev.langchain4j.data.message.UserMessage userMessage
                            && userMessage.singleText().contains("压缩为 1-2 句话的摘要"));

            assertTrue(foundCompressionPrompt, "配置 compressionModel 后应向模型发送压缩提示词");
        }

        @Test
        @DisplayName("hypergraph memory 未显式 include skill 时不应注册任何 capability")
        void shouldNotRegisterCapabilitiesWithoutExplicitSkillInclusion() throws Exception {
            Path tempDir = Files.createTempDirectory("runtime-hypergraph-memory-no-skill");
            String dsl = """
                        agent("hyper-memory-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            memory {
                                type "hypergraph"
                                sqliteJdbcUrl "%s"
                            }
                        }
                    """.formatted("jdbc:sqlite:" + tempDir.resolve("memory.db"));

            loadDsl(dsl);

            AgentInstance instance = registry.get("hyper-memory-agent");
            assertFalse(instance.getToolExecutors().containsKey("deep_recall"),
                    "未声明 skill 时不应注册 memory capability");
        }

        @Test
        @DisplayName("hypergraph memory 不声明 skill 时不应自动暴露任何 capability")
        void shouldNotAutoExposeCapabilitiesWithoutExplicitSkillDeclaration() throws Exception {
            Path tempDir = Files.createTempDirectory("runtime-hypergraph-memory-no-auto-expose");
            String dsl = """
                        agent("hyper-memory-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            memory {
                                type "hypergraph"
                                sqliteJdbcUrl "%s"
                            }
                        }
                    """.formatted("jdbc:sqlite:" + tempDir.resolve("memory.db"));

            loadDsl(dsl);

            AgentInstance instance = registry.get("hyper-memory-agent");
            assertFalse(instance.getToolExecutors().containsKey("deep_recall"),
                    "未声明 skill 时 deep_recall 不应自动注册");
            assertFalse(instance.getToolExecutors().containsKey("consolidate_memory"),
                    "未声明 skill 时 consolidate_memory 不应自动注册");
        }

        @Test
        @DisplayName("deep_recall 应可作为 DSL 内置 skill 注册")
        void shouldRegisterBuiltinDeepRecallSkill() throws Exception {
            Path tempDir = Files.createTempDirectory("runtime-hypergraph-memory-builtin-skill");
            String dsl = """
                        agent("hyper-memory-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            memory {
                                type "hypergraph"
                                stm {
                                    maxEdges 2
                                }
                                ltm {
                                    backend "sqlite"
                                    path "%s"
                                }
                            }
                            skills {
                                include "deep_recall"
                            }
                        }
                    """.formatted(tempDir.resolve("memory.db"));

            loadDsl(dsl);

            AgentInstance instance = registry.get("hyper-memory-agent");
            assertTrue(instance.getToolExecutors().containsKey("deep_recall"),
                    "内置 skill 应保留 deep_recall 工具名");
            assertFalse(instance.getToolExecutors().containsKey("consolidate_memory"),
                    "禁用 capability 暴露后不应额外暴露其他 memory capability");

            instance.getMemory().add(UserMessage.from("我喜欢黑咖啡，不加糖。"));
            instance.getMemory().add(UserMessage.from("请记住我的咖啡偏好。"));
            instance.getMemory().add(UserMessage.from("之后回答时可以直接引用这个偏好。"));
            stubModel.addToolCallResponse("deep_recall", "{\"query\":\"咖啡偏好\"}");
            stubModel.addTextResponse("用户提到了其咖啡偏好，希望记住。");  // ChatModelMemoryReconstructor 重建调用
            stubModel.addTextResponse("你偏好黑咖啡，不加糖。");

            String reply = executor.chat("hyper-memory-agent", "我的咖啡偏好是什么？");
            assertEquals("你偏好黑咖啡，不加糖。", reply);
        }

        @Test
        @DisplayName("内置 deep_recall skill 不应重复注册")
        void shouldAvoidDuplicateDeepRecallRegistration() throws Exception {
            Path tempDir = Files.createTempDirectory("runtime-hypergraph-memory-no-duplicate");
            String dsl = """
                        agent("hyper-memory-agent") {
                            model {
                                provider "ollama"
                                modelName "qwen:0.5b-chat"
                            }
                            memory {
                                type "hypergraph"
                                stm {
                                    maxEdges 2
                                }
                                ltm {
                                    backend "sqlite"
                                    path "%s"
                                }
                            }
                            skills {
                                include "deep_recall"
                            }
                        }
                    """.formatted(tempDir.resolve("memory.db"));

            loadDsl(dsl);

            AgentInstance instance = registry.get("hyper-memory-agent");
            long deepRecallCount = instance.getToolSpecifications().stream()
                    .filter(spec -> "deep_recall".equals(spec.name()))
                    .count();

            assertEquals(1L, deepRecallCount, "deep_recall 不应被重复注册");
            assertTrue(instance.getToolExecutors().containsKey("deep_recall"));
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
