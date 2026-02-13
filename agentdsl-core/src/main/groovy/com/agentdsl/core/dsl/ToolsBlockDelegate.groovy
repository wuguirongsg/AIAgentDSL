package com.agentdsl.core.dsl

import com.agentdsl.core.spec.AgentSpec
import com.agentdsl.core.spec.ToolSpec

/**
 * Tools 块的委托类。
 * 处理 tools { ... } 内部的 include 和 tool() 关键字。
 */
class ToolsBlockDelegate {

    private final AgentSpec agentSpec

    ToolsBlockDelegate(AgentSpec agentSpec) {
        this.agentSpec = agentSpec
    }

    /** include "toolName" — 引用已注册的工具 */
    void include(String toolName) {
        agentSpec.toolRefs << toolName
    }

    /** tool("name") { ... } — 内联定义工具 */
    void tool(String name, @DelegatesTo(ToolDelegate) Closure config) {
        def spec = new ToolSpec(name)
        def delegate = new ToolDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        agentSpec.tools << spec
    }
}
