package com.agentdsl.runtime.autonomous.pipeline;

import com.agentdsl.runtime.autonomous.model.ExecutionStrategy;
import com.agentdsl.runtime.autonomous.model.MonitorSignal;
import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import com.agentdsl.runtime.autonomous.model.StepContext;

/**
 * Pipeline 思考过程监听器。
 * 用于在 CLI 或其他界面展示 Agent 的内部思考过程，
 * 包括问题解构、策略规划、监控分析三个阶段。
 *
 * <p>使用方式：
 * <pre>{@code
 * AutonomousPipeline pipeline = AutonomousPipeline.builder()
 *     .decomposer(new LlmProblemDecomposer(model))
 *     .strategyPlanner(new TotStrategyPlanner(model))
 *     .monitor(new MetaCognitiveMonitor(80000, 0))
 *     .thoughtListener(new ConsolePipelineThoughtListener())  // 新增
 *     .build();
 * }</pre>
 *
 * @see AutonomousPipeline
 */
public interface PipelineThoughtListener {

    /**
     * Phase 1 完成：问题解构结果。
     *
     * @param problem 结构化的问题规格
     */
    void onDecomposition(ProblemSpec problem);

    /**
     * Phase 2 完成：策略规划结果。
     *
     * @param strategy 包含主路径和备用路径的执行策略
     */
    void onStrategyPlanning(ExecutionStrategy strategy);

    /**
     * Phase 4 每步：监控分析结果。
     *
     * @param step 当前步数
     * @param ctx  当前步的上下文快照
     * @param signal 监控信号（可能包含干预指令）
     */
    void onMonitorAnalysis(int step, StepContext ctx, MonitorSignal signal);

    /**
     * 空实现（默认不输出任何内容）。
     * 用于不需要输出思考过程的场景，避免空指针检查。
     */
    PipelineThoughtListener NOOP = new PipelineThoughtListener() {
        @Override
        public void onDecomposition(ProblemSpec problem) {
            // 无操作
        }

        @Override
        public void onStrategyPlanning(ExecutionStrategy strategy) {
            // 无操作
        }

        @Override
        public void onMonitorAnalysis(int step, StepContext ctx, MonitorSignal signal) {
            // 无操作
        }
    };
}
