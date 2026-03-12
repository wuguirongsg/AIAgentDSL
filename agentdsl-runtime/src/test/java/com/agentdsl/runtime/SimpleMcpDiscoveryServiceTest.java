package com.agentdsl.runtime;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimpleMcpDiscoveryServiceTest {

    /** 模拟官方 Registry API 返回含 npm+stdio 包的 JSON，验证完整 discover 流程。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDiscoverNpmServerFromRegistryApi() throws Exception {
        String apiJson = """
                {
                  "servers": [
                    {
                      "server": {
                        "name": "io.github.test/weather",
                        "description": "Weather MCP server",
                        "packages": [
                          {
                            "registryType": "npm",
                            "identifier": "@testuser/mcp-weather-server",
                            "version": "1.0.0",
                            "transport": { "type": "stdio" }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> apiResponse = mock(HttpResponse.class);
        when(apiResponse.statusCode()).thenReturn(200);
        when(apiResponse.body()).thenReturn(apiJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(apiResponse);

        SimpleMcpDiscoveryService service = new SimpleMcpDiscoveryService(mockClient);
        List<McpDiscoveryService.DiscoveredMcpServer> results =
                service.discover("mcp.so", "get_weather", "查天气");

        assertFalse(results.isEmpty());
        McpDiscoveryService.DiscoveredMcpServer first = results.get(0);
        assertEquals("npx", first.command().get(0));
        assertEquals("-y", first.command().get(1));
        assertEquals("@testuser/mcp-weather-server", first.command().get(2));
    }

    /** API 返回 docker 类型包时应正确构造 docker run 命令。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDiscoverDockerServerFromRegistryApi() throws Exception {
        String apiJson = """
                {
                  "servers": [
                    {
                      "server": {
                        "name": "io.github.test/github",
                        "description": "GitHub MCP server",
                        "packages": [
                          {
                            "registryType": "docker",
                            "identifier": "mcp/github",
                            "transport": { "type": "stdio" }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> apiResponse = mock(HttpResponse.class);
        when(apiResponse.statusCode()).thenReturn(200);
        when(apiResponse.body()).thenReturn(apiJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(apiResponse);

        SimpleMcpDiscoveryService service = new SimpleMcpDiscoveryService(mockClient);
        List<McpDiscoveryService.DiscoveredMcpServer> results =
                service.discover("mcp.so", "github_search", "搜索仓库");

        assertFalse(results.isEmpty());
        assertEquals("docker", results.get(0).command().get(0));
        assertTrue(results.get(0).command().contains("mcp/github"));
    }

    /** API 返回多个候选时应全部收集，便于调用方依次重试。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnMultipleCandidates() throws Exception {
        String apiJson = """
                {
                  "servers": [
                    {
                      "server": {
                        "name": "io.github.test/weather-a",
                        "packages": [{"registryType":"npm","identifier":"@a/mcp-weather","transport":{"type":"stdio"}}]
                      }
                    },
                    {
                      "server": {
                        "name": "io.github.test/weather-b",
                        "packages": [{"registryType":"npm","identifier":"@b/mcp-weather","transport":{"type":"stdio"}}]
                      }
                    }
                  ]
                }
                """;

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> apiResponse = mock(HttpResponse.class);
        when(apiResponse.statusCode()).thenReturn(200);
        when(apiResponse.body()).thenReturn(apiJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(apiResponse);

        SimpleMcpDiscoveryService service = new SimpleMcpDiscoveryService(mockClient);
        List<McpDiscoveryService.DiscoveredMcpServer> results =
                service.discover("mcp.so", "weather", "查天气");

        assertEquals(2, results.size());
        assertEquals("@a/mcp-weather", results.get(0).command().get(2));
        assertEquals("@b/mcp-weather", results.get(1).command().get(2));
    }

    /** API 返回 0 个服务时应返回空列表。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptyListWhenNoResults() throws Exception {
        String emptyJson = """
                { "servers": [] }
                """;

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(emptyJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        SimpleMcpDiscoveryService service = new SimpleMcpDiscoveryService(mockClient);
        List<McpDiscoveryService.DiscoveredMcpServer> results =
                service.discover("mcp.so", "nonexistent_tool", "some task");

        assertTrue(results.isEmpty());
    }

    /** 网络异常时应优雅返回空列表，不抛异常。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleNetworkError() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("Connection refused"));

        SimpleMcpDiscoveryService service = new SimpleMcpDiscoveryService(mockClient);
        List<McpDiscoveryService.DiscoveredMcpServer> results =
                service.discover("mcp.so", "some_tool", "some task");

        assertTrue(results.isEmpty());
    }

    /** pypi / 非 stdio transport 的包应被忽略。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipNonNpmOrNonStdioPackages() throws Exception {
        String apiJson = """
                {
                  "servers": [
                    {
                      "server": {
                        "name": "io.github.test/pypi-server",
                        "packages": [{"registryType":"pypi","identifier":"my-mcp-server","transport":{"type":"stdio"}}]
                      }
                    },
                    {
                      "server": {
                        "name": "io.github.test/http-server",
                        "packages": [{"registryType":"npm","identifier":"@x/mcp","transport":{"type":"http"}}]
                      }
                    }
                  ]
                }
                """;

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> apiResponse = mock(HttpResponse.class);
        when(apiResponse.statusCode()).thenReturn(200);
        when(apiResponse.body()).thenReturn(apiJson);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(apiResponse);

        SimpleMcpDiscoveryService service = new SimpleMcpDiscoveryService(mockClient);
        List<McpDiscoveryService.DiscoveredMcpServer> results =
                service.discover("mcp.so", "tool", "some task");

        assertTrue(results.isEmpty(), "pypi 和非 stdio 的包不应被选中");
    }
}
