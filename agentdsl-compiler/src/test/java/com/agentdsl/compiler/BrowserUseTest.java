package com.agentdsl.compiler;

import com.agentdsl.core.spec.AgentSpec;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 browser_use 配置解析是否符合预期。
 */
public class BrowserUseTest {

    @Test
    void testBrowserUseDslGeneration() {
        DslCompiler compiler = new DslCompiler();

        // 由于测试模块依赖问题，动态构建脚本内容直接编译
        String script = """
                    agent("web-operator") {
                        description "An agent that can operate the browser."
                        model { provider "ollama"; modelName "qwen3:4b" }
                        browser_use {
                            sandbox true
                            hitl_on "playwright_click", "playwright_fill"
                        }
                    }
                """;

        DslCompileResult result = compiler.compile(script);
        assertTrue(result.getDiagnostics().isEmpty(), "编译不应该有错误: " + result.getDiagnostics());

        AgentSpec agentSpec = result.getAgents().get(0);
        assertNotNull(agentSpec.getBrowserUse(), "应该解析到 BrowserUseSpec");
        assertTrue(agentSpec.getBrowserUse().isSandbox());
        assertTrue(agentSpec.getBrowserUse().getHitlActions().contains("playwright_click"));

        // 不再隐式生成 MCP 配置
        assertNull(agentSpec.getMcp(), "原生 Playwright 集成不应隐式生成 McpSpec");
    }
}
