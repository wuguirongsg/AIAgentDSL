package com.agentdsl.core.dsl

import com.agentdsl.core.spec.WorkflowSpec

/**
 * workflow("name") { ... } 闭包的委托。
 * 解析 description 和 steps 关键字。
 */
class WorkflowDelegate {

    final WorkflowSpec spec

    WorkflowDelegate(WorkflowSpec spec) {
        this.spec = spec
    }

    /**
     * description "工作流描述"
     */
    void description(String desc) {
        spec.description = desc
    }

    /**
     * steps { step(...); parallel { ... }; condition { ... }; loop(...) { ... } }
     */
    void steps(@DelegatesTo(StepsBlockDelegate) Closure config) {
        def delegate = new StepsBlockDelegate()
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.steps = delegate.steps
    }

}
