package com.agentdsl.runtime;

import java.util.List;
import java.util.Optional;

/**
 * 运行时 MCP 发现服务。
 * 根据缺失工具名与上下文，返回按优先级排序的可启动 MCP Server 候选列表。
 * 调用方应依次尝试每个候选，直到成功连接。
 */
public interface McpDiscoveryService {

    /**
     * 搜索并返回所有匹配的候选 MCP Server，按优先级从高到低排列。
     * 返回空列表表示未找到任何候选。
     */
    List<DiscoveredMcpServer> discover(String registry, String missingToolName, String userMessage);

    record DiscoveredMcpServer(String serverName, List<String> command) {
    }
}
