package com.agentdsl.runtime.autonomous.pipeline;

import com.agentdsl.runtime.autonomous.model.MonitorSignal;
import com.agentdsl.runtime.autonomous.model.StepContext;

/**
 * Phase 4 接口：执行监控器。
 * 持续监控 ReAct 执行循环的健康状态，在检测到异常时返回干预信号。
 *
 * <p>约定：每步 ReAct 执行结束后调用一次 {@link #analyze(StepContext)}。
 * 实现必须是有状态的（记录历史），因此每次任务开始前须调用 {@link #reset()}。
 *
 * <p>内置实现：
 * <ul>
 *   <li>{@code MetaCognitiveMonitor} — 四维度：停滞/置信度下降/资源超限/矛盾检测</li>
 *   <li>{@code BasicStagnationMonitor} — 仅停滞检测，零 LLM 成本</li>
 * </ul>
 */
public interface ExecutionMonitor {

    /**
     * 分析当前执行状态，返回监控信号。
     *
     * @param ctx 本步执行上下文（LLM输出、工具调用、token 消耗）
     * @return 监控信号；{@link MonitorSignal#isHealthy()} 为 true 表示无需干预
     */
    MonitorSignal analyze(StepContext ctx);

    /**
     * 重置监控器内部状态（每次新任务开始前调用）。
     */
    void reset();
}
