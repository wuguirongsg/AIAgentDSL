package com.agentdsl.core.dsl

import com.agentdsl.core.spec.McpServerSpec

/**
 * MCP Server 块的委托类。
 * 处理 server("name") { transport "stdio"; command "npx", ... } 内部关键字。
 */
class McpServerDelegate {

    final McpServerSpec spec

    McpServerDelegate(String name) {
        this.spec = new McpServerSpec(name)
    }

    /** transport "stdio" 或 "http" 或 "sse" */
    void transport(String type) {
        spec.transport = type
    }

    /** command "npx", "-y", "@modelcontextprotocol/server-github" */
    void command(String... parts) {
        spec.command = parts as List
    }

    /** url "http://localhost:8080/mcp" */
    void url(String endpoint) {
        spec.url = endpoint
    }

    /** env "KEY", "value" */
    void env(String key, String value) {
        spec.env.put(key, value)
    }

    /** timeout 30 */
    void timeout(int seconds) {
        spec.timeout = seconds
    }

    /** logEvents true */
    void logEvents(boolean enabled) {
        spec.logEvents = enabled
    }

    McpServerSpec build() {
        return spec
    }

}
