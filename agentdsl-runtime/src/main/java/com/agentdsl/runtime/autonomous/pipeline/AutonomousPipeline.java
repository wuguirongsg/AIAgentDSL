package com.agentdsl.runtime.autonomous.pipeline;

import com.agentdsl.runtime.autonomous.model.ExecutionStrategy;
import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 自主执行 Pipeline。
 * 将 Phase 1（问题解构）和 Phase 2（策略规划）串联起来，
 * 返回充有数据的 {@link PipelineContext} 供 Phase 3（ReAct 执行）使用。
 *
 * <p>Phase 4（ExecutionMonitor）作为独立接口暴露，由 ReAct 循环按步调用。
 *
 * <p>通过 {@link Builder} 构建，支持任意实现组合：
 * <pre>{@code
 * AutonomousPipeline pipeline = AutonomousPipeline.builder()
 *     .decomposer(new LlmProblemDecomposer(model))
 *     .strategyPlanner(new TotStrategyPlanner(model))
 *     .monitor(new MetaCognitiveMonitor(80000, 0))
 *     .build();
 *
 * PipelineContext ctx = pipeline.prepare(userGoal, tools);
 * MonitorSignal signal = pipeline.getMonitor().analyze(stepCtx);
 * }</pre>
 */
public class AutonomousPipeline {

    private static final Logger log = LoggerFactory.getLogger(AutonomousPipeline.class);

    private final ProblemDecomposer decomposer;
    private final StrategyPlanner strategyPlanner;
    private final ExecutionMonitor monitor;

    private AutonomousPipeline(Builder builder) {
        this.decomposer = builder.decomposer;
        this.strategyPlanner = builder.strategyPlanner;
        this.monitor = builder.monitor;
    }

    /**
     * 执行 Phase 1 + Phase 2，返回充有数据的上下文。
     * Phase 3/4 使用此上下文。
     */
    public PipelineContext prepare(String userGoal, List<ToolSpecification> tools) {
        PipelineContext ctx = new PipelineContext();

        // Phase 1：问题解构
        log.debug("Pipeline Phase 1 start: decomposing goal");
        ProblemSpec problem = decomposer.decompose(userGoal, tools);
        ctx.setProblemSpec(problem);
        log.debug("Pipeline Phase 1 complete: {}", problem);

        // Phase 2：策略规划
        log.debug("Pipeline Phase 2 start: planning strategy");
        ExecutionStrategy strategy = strategyPlanner.plan(problem, tools);
        ctx.setExecutionStrategy(strategy);
        log.debug("Pipeline Phase 2 complete: {}", strategy);

        // Phase 4 监控器重置（新任务）
        monitor.reset();

        return ctx;
    }

    // ── 访问器 ────────────────────────────────────────────────────────

    /** Phase 1 组件（测试或日志用）。 */
    public ProblemDecomposer getDecomposer() { return decomposer; }

    /** Phase 2 组件（测试或日志用）。 */
    public StrategyPlanner getStrategyPlanner() { return strategyPlanner; }

    /**
     * Phase 4 组件。由 ReAct 循环每步调用：
     * <pre>{@code monitor.analyze(stepCtx)}</pre>
     */
    public ExecutionMonitor getMonitor() { return monitor; }

    // ── Builder ───────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private ProblemDecomposer decomposer;
        private StrategyPlanner strategyPlanner;
        private ExecutionMonitor monitor;

        public Builder decomposer(ProblemDecomposer d) {
            this.decomposer = d;
            return this;
        }

        public Builder strategyPlanner(StrategyPlanner p) {
            this.strategyPlanner = p;
            return this;
        }

        public Builder monitor(ExecutionMonitor m) {
            this.monitor = m;
            return this;
        }

        public AutonomousPipeline build() {
            if (decomposer == null || strategyPlanner == null || monitor == null) {
                throw new IllegalStateException(
                    "AutonomousPipeline requires decomposer, strategyPlanner, and monitor");
            }
            return new AutonomousPipeline(this);
        }
    }
}
