package com.agentdsl.compiler;

import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.McpServerSpec;
import com.agentdsl.core.spec.McpSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP DSL 语法解析测试。
 * 验证 mcp { server(...) { } } 语法能正确解析为 McpSpec。
 */
class McpDslParsingTest {

    private DslCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DslCompiler();
    }

    @Test
    void shouldParseStdioMcpServer() {
        String dsl = """
                agent("mcp-agent") {
                    description "Agent with MCP"
                    model {
                        provider "ollama"
                        modelName "qwen:0.5b-chat"
                    }
                    mcp {
                        server("github") {
                            transport "stdio"
                            command "npx", "-y", "@modelcontextprotocol/server-github"
                            env "GITHUB_TOKEN", "test-token"
                            timeout 30
                            logEvents true
                        }
                    }
                }
                """;

        DslCompileResult result = compiler.compile(dsl);

        assertNotNull(result);
        assertEquals(1, result.getAgents().size());

        AgentSpec agent = result.getAgents().get(0);
        assertEquals("mcp-agent", agent.getName());

        McpSpec mcp = agent.getMcp();
        assertNotNull(mcp, "MCP spec should not be null");
        assertEquals(1, mcp.getServers().size());

        McpServerSpec server = mcp.getServers().get(0);
        assertEquals("github", server.getName());
        assertEquals("stdio", server.getTransport());
        assertEquals(List.of("npx", "-y", "@modelcontextprotocol/server-github"), server.getCommand());
        assertEquals("test-token", server.getEnv().get("GITHUB_TOKEN"));
        assertEquals(30, server.getTimeout());
        assertTrue(server.isLogEvents());
    }

    @Test
    void shouldParseHttpMcpServer() {
        String dsl = """
                agent("http-mcp") {
                    description "Agent with HTTP MCP"
                    model {
                        provider "openai"
                        modelName "gpt-4"
                    }
                    mcp {
                        server("remote") {
                            transport "http"
                            url "http://localhost:8080/mcp"
                            timeout 60
                        }
                    }
                }
                """;

        DslCompileResult result = compiler.compile(dsl);
        AgentSpec agent = result.getAgents().get(0);

        McpSpec mcp = agent.getMcp();
        assertNotNull(mcp);

        McpServerSpec server = mcp.getServers().get(0);
        assertEquals("remote", server.getName());
        assertEquals("http", server.getTransport());
        assertEquals("http://localhost:8080/mcp", server.getUrl());
        assertEquals(60, server.getTimeout());
        assertFalse(server.isLogEvents());
    }

    @Test
    void shouldParseMultipleServersWithFilter() {
        String dsl = """
                agent("multi-mcp") {
                    description "Agent with multiple MCP servers"
                    model {
                        provider "ollama"
                        modelName "qwen:0.5b-chat"
                    }
                    mcp {
                        server("github") {
                            transport "stdio"
                            command "npx", "-y", "@modelcontextprotocol/server-github"
                        }
                        server("filesystem") {
                            transport "stdio"
                            command "npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"
                        }
                        filterTools "list_repos", "read_file"
                    }
                }
                """;

        DslCompileResult result = compiler.compile(dsl);
        AgentSpec agent = result.getAgents().get(0);

        McpSpec mcp = agent.getMcp();
        assertNotNull(mcp);
        assertEquals(2, mcp.getServers().size());
        assertEquals("github", mcp.getServers().get(0).getName());
        assertEquals("filesystem", mcp.getServers().get(1).getName());

        // 验证工具过滤
        assertNotNull(mcp.getFilterToolNames());
        assertEquals(2, mcp.getFilterToolNames().size());
        assertTrue(mcp.getFilterToolNames().contains("list_repos"));
        assertTrue(mcp.getFilterToolNames().contains("read_file"));
    }

    @Test
    void shouldParseAgentWithoutMcp() {
        String dsl = """
                agent("no-mcp") {
                    description "Agent without MCP"
                    model {
                        provider "ollama"
                        modelName "qwen:0.5b-chat"
                    }
                }
                """;

        DslCompileResult result = compiler.compile(dsl);
        AgentSpec agent = result.getAgents().get(0);
        assertNull(agent.getMcp(), "MCP spec should be null when not configured");
    }
}
