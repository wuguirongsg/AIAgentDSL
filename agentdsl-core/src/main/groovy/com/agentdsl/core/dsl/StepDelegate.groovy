package com.agentdsl.core.dsl

import com.agentdsl.core.spec.StepSpec

/**
 * step("name") { ... } 闭包的委托。
 * 解析 agent、input、output 关键字。
 */
class StepDelegate {

    final StepSpec spec

    StepDelegate(StepSpec spec) {
        this.spec = spec
    }

    /**
     * agent "agent-name" — 指定执行该步骤的 Agent
     */
    void agent(String agentName) {
        spec.agentRef = agentName
    }

    /**
     * input { prevResult -> ... } — 输入转换闭包
     */
    void input(Closure<?> transform) {
        spec.inputTransform = transform
    }

    /**
     * output { result -> ... } — 输出转换闭包
     */
    void output(Closure<?> transform) {
        spec.outputTransform = transform
    }
}
