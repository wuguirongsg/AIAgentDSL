package com.agentdsl.core.dsl

import com.agentdsl.core.spec.McpSpec

/**
 * MCP 顶层块的委托类。
 * 处理 mcp { server(...) { }; filterTools ... } 语法。
 */
class McpBlockDelegate {

    final McpSpec spec = new McpSpec()

    /** server("name") { transport "stdio"; command ... } */
    void server(String name, @DelegatesTo(McpServerDelegate) Closure config) {
        def delegate = new McpServerDelegate(name)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.servers.add(delegate.build())
    }

    /** filterTools "tool1", "tool2" — 只暴露指定工具 */
    void filterTools(String... toolNames) {
        spec.filterToolNames = toolNames as List
    }

}
