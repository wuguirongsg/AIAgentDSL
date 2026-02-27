package com.agentdsl.core.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 配置聚合模型。
 * 包含多个 MCP Server 配置和可选的工具名过滤列表。
 * 对应 DSL 中的 mcp { ... } 块。
 */
public class McpSpec {

    private List<McpServerSpec> servers = new ArrayList<>();
    private List<String> filterToolNames; // 可选：只暴露指定工具

    public McpSpec() {
    }

    // --- Getters & Setters ---

    public List<McpServerSpec> getServers() {
        return servers;
    }

    public void setServers(List<McpServerSpec> servers) {
        this.servers = servers;
    }

    public List<String> getFilterToolNames() {
        return filterToolNames;
    }

    public void setFilterToolNames(List<String> filterToolNames) {
        this.filterToolNames = filterToolNames;
    }

    @Override
    public String toString() {
        return "McpSpec{servers=" + servers.size() +
                ", filterToolNames=" + (filterToolNames != null ? filterToolNames.size() : 0) + "}";
    }
}
