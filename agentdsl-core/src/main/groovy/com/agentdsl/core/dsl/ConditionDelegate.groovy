package com.agentdsl.core.dsl

import com.agentdsl.core.spec.StepSpec

/**
 * condition { ... } 闭包的委托。
 * 解析 check 和 on 关键字。
 */
class ConditionDelegate {

    final StepSpec spec

    ConditionDelegate(StepSpec spec) {
        this.spec = spec
    }

    /**
     * check { result -> result.level } — 条件判断闭包
     */
    void check(Closure<?> closure) {
        spec.checkClosure = closure
    }

    /**
     * on "value" { step("xxx") { ... } } — 条件分支
     */
    void on(String value, @DelegatesTo(StepsBlockDelegate) Closure config) {
        def innerDelegate = new StepsBlockDelegate()
        config.delegate = innerDelegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.branches[value] = innerDelegate.steps
    }

}
