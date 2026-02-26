package com.agentdsl.core.dsl

import com.agentdsl.core.spec.StepSpec

/**
 * loop(maxIterations: N) { ... } 闭包的委托。
 * 解析 step()、until {} 关键字。
 * 循环体中的步骤和 until 条件会按声明顺序记录。
 */
class LoopDelegate {

    final StepSpec spec

    LoopDelegate(StepSpec spec) {
        this.spec = spec
    }

    /**
     * step("name") { agent "xxx"; ... } — 循环体内的步骤
     */
    void step(String name, @DelegatesTo(StepDelegate) Closure config) {
        def stepSpec = new StepSpec(StepSpec.StepType.SEQUENTIAL, name)
        def delegate = new StepDelegate(stepSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.loopBody << stepSpec
    }

    /**
     * until { result -> result.score >= 0.9 } — 循环终止条件
     */
    void until(Closure<?> closure) {
        spec.untilClosure = closure
    }

}
