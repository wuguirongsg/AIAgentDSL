package com.agentdsl.core.dsl

import com.agentdsl.core.spec.ParameterSpec
import com.agentdsl.core.spec.ToolSpec

/**
 * Tool 定义的委托类。
 * 处理 tool("name") { ... } 内部的关键字。
 */
class ToolDelegate {

    private final ToolSpec spec

    ToolDelegate(ToolSpec spec) {
        this.spec = spec
    }

    /** description "工具描述" */
    void description(String desc) {
        spec.description = desc
    }

    /** parameter { name "x"; type "string"; ... } */
    void parameter(@DelegatesTo(ParameterDelegate) Closure config) {
        def paramSpec = new ParameterSpec()
        def delegate = new ParameterDelegate(paramSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.parameters << paramSpec
    }

    /** execute { params -> ... } */
    void execute(Closure body) {
        spec.executeBody = body
    }
}
