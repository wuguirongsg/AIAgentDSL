package com.agentdsl.tools.builtin;

import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SearchSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    private WebSearchTool webSearchTool;

    @BeforeEach
    void setUp() {
        webSearchTool = new WebSearchTool();
    }

    @Test
    @DisplayName("没有配置 API_KEY 时应返回包含 Error 的提示")
    void shouldReturnErrorWhenNoApiKeyConfigured() {
        AgentSpec agentSpec = new AgentSpec();
        SearchSpec searchSpec = new SearchSpec();
        // 设置 provider 为一个不存在的或无效的 key 以确保触发空校验
        searchSpec.setProvider("tavily");
        searchSpec.setApiKey(""); // 清空或者未设置
        agentSpec.setSearchConfig(searchSpec);
        webSearchTool.setCurrentAgentSpec(agentSpec);

        // 为了测试稳健，我们将环境变量模拟为空或预期其为空（System.getenv 无法直接 mock，故依赖于 CI 或本地无对应真实 KEY）
        // 假设当前环境中没有名为 "TAVILY_API_KEY" 的有效环境变量，如果没有，则会命中错误提示。
        String result = webSearchTool.webSearch("测试查询", null);

        // 如果真的有系统环境变量则该测试可能返回失败请求或成功结果，仅测试 "Error" / "缺少 API Key" 分支
        if (System.getenv("TAVILY_API_KEY") == null || System.getenv("TAVILY_API_KEY").isEmpty()) {
            assertTrue(result.contains("Error: 缺少 API Key"));
        }
    }

    @Test
    @DisplayName("不支持的 provider 应返回明确的错误")
    void shouldReturnErrorForUnsupportedProvider() {
        AgentSpec agentSpec = new AgentSpec();
        SearchSpec searchSpec = new SearchSpec();
        searchSpec.setProvider("invalid_provider");
        searchSpec.setApiKey("fake-key");
        agentSpec.setSearchConfig(searchSpec);
        webSearchTool.setCurrentAgentSpec(agentSpec);

        String result = webSearchTool.webSearch("测试", null);
        assertTrue(result.contains("Error: 不支持的搜索提供商 'invalid_provider'"));
    }

    @Test
    @DisplayName("参数覆写（providerOverride）机制应生效")
    void shouldOverrideProviderWhenSpecifiedAsArgument() {
        // 全局配置 Serper
        AgentSpec agentSpec = new AgentSpec();
        SearchSpec searchSpec = new SearchSpec();
        searchSpec.setProvider("serper");
        searchSpec.setApiKey("fake-serper-key");
        agentSpec.setSearchConfig(searchSpec);
        webSearchTool.setCurrentAgentSpec(agentSpec);

        // 显式在入参覆盖为不支持的 provider 以观察输出
        String result = webSearchTool.webSearch("测试", "invalid_override");
        assertTrue(result.contains("Error: 不支持的搜索提供商 'invalid_override'"),
                "提供商参数应该被覆盖并触发不支持错误： " + result);
    }
}
