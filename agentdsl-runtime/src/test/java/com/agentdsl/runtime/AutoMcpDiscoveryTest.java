package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.langchain4j.LangChainMemoryFactory;
import com.agentdsl.langchain4j.LangChainRagFactory;
import com.agentdsl.langchain4j.LangChainToolBridge;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMcpDiscoveryTest {

    private StubChatModel stubModel;
    private TestingAgentRegistry registry;
    private AgentExecutor executor;
    private DslCompiler compiler;

    @BeforeEach
    void setUp() {
        stubModel = new StubChatModel();
        StubModelFactory modelFactory = new StubModelFactory(stubModel);
        registry = new TestingAgentRegistry(modelFactory, new LangChainMemoryFactory(),
                new LangChainToolBridge(), new LangChainRagFactory());
        executor = new AgentExecutor(registry);
        compiler = new DslCompiler();
    }

    @Test
    void shouldAutoDiscoverMissingToolWhenEnabled() {
        String dsl = """
                agent("auto-bot") {
                    model {
                        provider "ollama"
                        modelName "qwen:0.5b-chat"
                    }
                    auto_discover_mcp true
                    mcp_registry "mcp.so"
                }
                """;

        stubModel.addToolCallResponse("weather_lookup", "{\"city\":\"杭州\"}");
        stubModel.addTextResponse("已完成天气查询。");

        loadDsl(dsl);
        String reply = executor.chat("auto-bot", "帮我查杭州天气");

        assertEquals("已完成天气查询。", reply);
        assertEquals(1, registry.discoveryAttempts.get());
        assertTrue(registry.get("auto-bot").getToolExecutors().containsKey("weather_lookup"));
    }

    private void loadDsl(String dslScript) {
        DslCompileResult result = compiler.compile(dslScript);
        for (AgentSpec agent : result.getAgents()) {
            registry.register(agent);
        }
    }

    private static class TestingAgentRegistry extends AgentRegistry {
        private final AtomicInteger discoveryAttempts = new AtomicInteger(0);

        TestingAgentRegistry(StubModelFactory modelFactory,
                LangChainMemoryFactory memoryFactory,
                LangChainToolBridge toolBridge,
                LangChainRagFactory ragFactory) {
            super(modelFactory, memoryFactory, toolBridge, ragFactory);
        }

        @Override
        public synchronized boolean tryAutoDiscoverAndAttachTool(AgentInstance instance,
                String missingToolName, String userMessage) {
            discoveryAttempts.incrementAndGet();
            // 主动发现时 missingToolName 为空，根据 userMessage 推断；被动发现时直接用 missingToolName
            String toolName = (missingToolName == null || missingToolName.isBlank())
                    ? "weather_lookup"
                    : missingToolName;
            ToolSpecification dynamicTool = ToolSpecification.builder()
                    .name(toolName)
                    .description("dynamic tool")
                    .build();
            ToolExecutor dynamicExecutor = (request, memoryId) -> "dynamic-result";
            instance.getToolSpecifications().add(dynamicTool);
            instance.getToolExecutors().put(toolName, dynamicExecutor);
            return true;
        }
    }
}
