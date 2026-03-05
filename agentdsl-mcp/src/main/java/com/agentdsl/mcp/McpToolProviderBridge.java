package com.agentdsl.mcp;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.McpServerSpec;
import com.agentdsl.core.spec.McpSpec;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具提供者桥接。
 * 读取 McpSpec 配置 → 创建 MCP Transport → 构建 MCP Client → 获取工具列表和执行器。
 * <p>
 * 直接使用 McpClient 的 listTools() 和 executeTool() API，
 * 将 MCP 工具转换为标准的 ToolSpecification + ToolExecutor，
 * 以便 AgentExecutor 统一处理。
 */
public class McpToolProviderBridge {

    private static final Logger log = LoggerFactory.getLogger(McpToolProviderBridge.class);

    /**
     * MCP 连接结果，包含客户端列表、工具规范和执行器映射。
     */
    public record McpToolsResult(
            List<McpClient> clients,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors) {
        public void close() {
            for (McpClient client : clients) {
                try {
                    client.close();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
        }
    }

    /**
     * 根据 McpSpec 创建 MCP 连接，返回工具规范和执行器。
     */
    public McpToolsResult connect(McpSpec mcpSpec) {
        List<McpClient> clients = new ArrayList<>();
        List<ToolSpecification> allToolSpecs = new ArrayList<>();
        Map<String, ToolExecutor> allToolExecutors = new HashMap<>();

        for (McpServerSpec serverSpec : mcpSpec.getServers()) {
            log.info("连接 MCP Server: {} (transport: {})", serverSpec.getName(), serverSpec.getTransport());

            // 1. 创建 Transport
            McpTransport transport = createTransport(serverSpec);

            // 2. 创建 Client
            DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .clientName("agentdsl-" + serverSpec.getName());

            if (serverSpec.getTimeout() > 0) {
                clientBuilder.toolExecutionTimeout(Duration.ofSeconds(serverSpec.getTimeout()));
                clientBuilder.initializationTimeout(Duration.ofSeconds(serverSpec.getTimeout()));
            }

            McpClient client = clientBuilder.build();
            clients.add(client);

            // 3. 获取工具列表
            List<ToolSpecification> toolSpecs = client.listTools();
            log.info("MCP Server '{}' 提供 {} 个工具", serverSpec.getName(), toolSpecs.size());

            // 4. 过滤工具（可选）
            List<String> filterNames = mcpSpec.getFilterToolNames();
            for (ToolSpecification spec : toolSpecs) {
                if (filterNames != null && !filterNames.isEmpty()
                        && !filterNames.contains(spec.name())) {
                    continue;
                }
                allToolSpecs.add(spec);
                // 创建 ToolExecutor 包装 McpClient.executeTool
                final McpClient mcpClient = client;
                allToolExecutors.put(spec.name(), (toolExecutionRequest, memoryId) -> {
                    try {
                        return mcpClient.executeTool(toolExecutionRequest);
                    } catch (Exception e) {
                        log.error("MCP 工具 '{}' 执行失败: {}", toolExecutionRequest.name(), e.getMessage(), e);
                        return "Error executing MCP tool: " + e.getMessage();
                    }
                });
            }

            log.info("MCP Server '{}' 已连接", serverSpec.getName());
        }

        log.info("MCP 桥接完成: {} 个 Server, {} 个工具", clients.size(), allToolSpecs.size());
        return new McpToolsResult(clients, allToolSpecs, allToolExecutors);
    }

    /**
     * 根据 McpServerSpec 创建对应的传输层。
     */
    private McpTransport createTransport(McpServerSpec spec) {
        return switch (spec.getTransport()) {
            case "stdio" -> {
                if (spec.getCommand() == null || spec.getCommand().isEmpty()) {
                    throw new DslRuntimeException("ADSL-050",
                            "MCP Server '" + spec.getName() + "' 使用 STDIO 传输但未配置 command");
                }
                StdioMcpTransport.Builder builder = new StdioMcpTransport.Builder()
                        .command(spec.getCommand());
                if (spec.getEnv() != null && !spec.getEnv().isEmpty()) {
                    builder.environment(spec.getEnv());
                }
                if (spec.isLogEvents()) {
                    builder.logEvents(true);
                }
                yield builder.build();
            }
            case "http", "sse" -> {
                if (spec.getUrl() == null || spec.getUrl().isBlank()) {
                    throw new DslRuntimeException("ADSL-050",
                            "MCP Server '" + spec.getName() + "' 使用 HTTP/SSE 传输但未配置 url");
                }
                HttpMcpTransport.Builder builder = new HttpMcpTransport.Builder()
                        .sseUrl(spec.getUrl())
                        .logRequests(spec.isLogEvents())
                        .logResponses(spec.isLogEvents());
                if (spec.getTimeout() > 0) {
                    builder.timeout(Duration.ofSeconds(spec.getTimeout()));
                }
                yield builder.build();
            }
            default -> throw new DslRuntimeException("ADSL-050",
                    "不支持的 MCP 传输方式: " + spec.getTransport()
                            + "。支持: stdio, http, sse");
        };
    }
}
