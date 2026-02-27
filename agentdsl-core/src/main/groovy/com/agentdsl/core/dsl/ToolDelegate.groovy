package com.agentdsl.core.dsl

import com.agentdsl.core.spec.ParameterSpec
import com.agentdsl.core.spec.ToolSpec
import com.agentdsl.core.spec.PermissionSpec

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

    void returns(String type, String description = null) {
        spec.returnType = type
        if (description != null) {
            spec.returnDescription = description
        }
    }

    void timeout(int seconds) {
        spec.timeoutSeconds = seconds
    }

    void onError(Closure handler) {
        spec.onErrorHandler = handler
    }

    void permissions(@DelegatesTo(PermissionDelegate) Closure config) {
        def permSpec = new com.agentdsl.core.spec.PermissionSpec()
        def delegate = new PermissionDelegate(permSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.permissions = permSpec
    }

}
