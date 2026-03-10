package com.agentdsl.core.dsl

import com.agentdsl.core.spec.AutonomousSpec

/**
 * autonomous { ... } 块的委托类。
 * 处理自主 Agent 配置关键字。
 *
 * <pre>
 * autonomous {
 *     execution_mode "plan"   // "plan" | "fast"
 *     max_steps 10
 * }
 * </pre>
 */
class AutonomousDelegate {

    private final AutonomousSpec spec

    AutonomousDelegate(AutonomousSpec spec) {
        this.spec = spec
    }

    /** execution_mode "plan" 或 "fast" */
    void execution_mode(String mode) {
        spec.executionMode = mode
    }

    /** max_steps 10 */
    void max_steps(int steps) {
        spec.maxSteps = steps
    }

}
