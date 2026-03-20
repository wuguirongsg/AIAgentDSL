package com.agentdsl.core.dsl

import com.agentdsl.core.spec.AutonomousSpec

/**
 * autonomous { ... } 块的委托类。
 * 处理自主 Agent 配置关键字。
 *
 * <pre>
 * autonomous {
 *     execution_mode "plan"       // "plan" | "fast"
 *     max_steps 10
 *     preset "smart"              // "smart" | "plan" | "fast"  Pipeline 预设
 *     max_token_budget 80000      // 元认知监控 Token 预算
 *     max_time_ms 300000          // 元认知监控时间预算（毫秒）
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

    /**
     * preset "smart" | "plan" | "fast"
     * 决定 Pipeline 使用哪组实现组合：
     * - smart：全套四阶段（LLM解构 + ToT规划 + 四维监控）
     * - plan ：中等（LLM解构 + 线性规划 + 停滞监控）默认值
     * - fast ：轻量（启发式解构 + 线性规划 + 停滞监控）
     */
    void preset(String p) {
        spec.preset = p
    }

    /**
     * max_token_budget 80000
     * 元认知监控的 Token 预算（0 = 不限制）。
     */
    void max_token_budget(int budget) {
        spec.maxTokenBudget = budget
    }

    /**
     * max_time_ms 300000
     * 元认知监控的时间预算（毫秒，0 = 不限制）。
     */
    void max_time_ms(long ms) {
        spec.maxTimeMs = ms
    }
}
