package com.agentdsl.core.dsl

import com.agentdsl.core.spec.StepSpec

/**
 * steps { ... } 闭包的委托。
 * 解析 step()、parallel {}、condition {}、loop() 关键字。
 */
class StepsBlockDelegate {

    final List<StepSpec> steps = []

    /**
     * step("name") { agent "xxx"; input { ... }; output { ... } }
     */
    void step(String name, @DelegatesTo(StepDelegate) Closure config) {
        def spec = new StepSpec(StepSpec.StepType.SEQUENTIAL, name)
        def delegate = new StepDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        steps << spec
    }

    /**
     * parallel { step("a") { ... }; step("b") { ... } }
     */
    void parallel(@DelegatesTo(StepsBlockDelegate) Closure config) {
        def spec = new StepSpec(StepSpec.StepType.PARALLEL)
        def innerDelegate = new StepsBlockDelegate()
        config.delegate = innerDelegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.parallelSteps = innerDelegate.steps
        steps << spec
    }

    /**
     * condition { check { ... }; on "value" { ... } }
     */
    void condition(@DelegatesTo(ConditionDelegate) Closure config) {
        def spec = new StepSpec(StepSpec.StepType.CONDITION)
        def delegate = new ConditionDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        steps << spec
    }

    /**
     * loop(maxIterations: N) { step(...); until { ... }; step(...) }
     */
    void loop(Map<String, Object> params, @DelegatesTo(LoopDelegate) Closure config) {
        def spec = new StepSpec(StepSpec.StepType.LOOP)
        if (params.containsKey('maxIterations')) {
            spec.maxIterations = params.maxIterations as int
        }
        def delegate = new LoopDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        steps << spec
    }

}
