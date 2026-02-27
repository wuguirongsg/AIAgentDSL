package com.agentdsl.mcp;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.McpServerSpec;
import com.agentdsl.core.spec.McpSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolProviderBridge 单元测试。
 * 注意：真实 MCP 连接测试需要 npx / MCP Server 环境，
 * 此处仅测试配置验证和异常处理逻辑。
 */
class McpToolProviderBridgeTest {

    private final McpToolProviderBridge bridge = new McpToolProviderBridge();

    @Test
    void shouldRejectStdioWithoutCommand() {
        McpSpec spec = new McpSpec();
        McpServerSpec server = new McpServerSpec("test");
        server.setTransport("stdio");
        // 不设置 command
        spec.getServers().add(server);

        DslRuntimeException ex = assertThrows(DslRuntimeException.class,
                () -> bridge.connect(spec));
        assertTrue(ex.getMessage().contains("未配置 command"));
    }

    @Test
    void shouldRejectHttpWithoutUrl() {
        McpSpec spec = new McpSpec();
        McpServerSpec server = new McpServerSpec("test");
        server.setTransport("http");
        // 不设置 url
        spec.getServers().add(server);

        DslRuntimeException ex = assertThrows(DslRuntimeException.class,
                () -> bridge.connect(spec));
        assertTrue(ex.getMessage().contains("未配置 url"));
    }

    @Test
    void shouldRejectSseWithoutUrl() {
        McpSpec spec = new McpSpec();
        McpServerSpec server = new McpServerSpec("test");
        server.setTransport("sse");
        spec.getServers().add(server);

        DslRuntimeException ex = assertThrows(DslRuntimeException.class,
                () -> bridge.connect(spec));
        assertTrue(ex.getMessage().contains("未配置 url"));
    }

    @Test
    void shouldRejectUnsupportedTransport() {
        McpSpec spec = new McpSpec();
        McpServerSpec server = new McpServerSpec("test");
        server.setTransport("websocket");
        server.setCommand(List.of("dummy"));
        spec.getServers().add(server);

        DslRuntimeException ex = assertThrows(DslRuntimeException.class,
                () -> bridge.connect(spec));
        assertTrue(ex.getMessage().contains("不支持的 MCP 传输方式"));
    }

    @Test
    void shouldHaveCorrectDefaults() {
        McpServerSpec server = new McpServerSpec("test");
        assertEquals("stdio", server.getTransport());
        assertEquals(60, server.getTimeout());
        assertFalse(server.isLogEvents());
        assertNotNull(server.getEnv());
        assertTrue(server.getEnv().isEmpty());
    }
}
