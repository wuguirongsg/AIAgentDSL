package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.core.spec.AutonomousSpec;
import com.agentdsl.runtime.autonomous.pipeline.AutonomousPipeline;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Pipeline 工厂。
 * 根据 {@link AutonomousSpec#getPreset()} 将不同的接口实现组装为 {@link AutonomousPipeline}。
 *
 * <h3>Preset 对应关系</h3>
 * <table>
 *   <tr><th>Preset</th><th>分解器</th><th>规划器</th><th>监控器</th></tr>
 *   <tr><td>smart</td><td>LlmProblemDecomposer</td><td>TotStrategyPlanner</td><td>MetaCognitiveMonitor（全维度）</td></tr>
 *   <tr><td>plan</td><td>LlmProblemDecomposer</td><td>LinearStrategyPlanner</td><td>BasicStagnationMonitor</td></tr>
 *   <tr><td>fast</td><td>DefaultProblemDecomposer</td><td>LinearStrategyPlanner</td><td>BasicStagnationMonitor</td></tr>
 * </table>
 */
public class PipelineFactory {

    /** 默认 Token 预算（80k）。 */
    private static final int DEFAULT_TOKEN_BUDGET = 80_000;

    private PipelineFactory() {}

    /**
     * 根据 AutonomousSpec 创建对应的 Pipeline。
     *
     * @param config Agent 的自主配置
     * @param model  ChatModel 实例（LLM 型组件需要）
     * @return 组装好的 Pipeline
     */
    public static AutonomousPipeline create(AutonomousSpec config, ChatModel model) {
        String preset = config.getPreset();

        return switch (preset.toLowerCase()) {
            case "smart"  -> smartPipeline(model, config);
            case "fast"   -> fastPipeline(model);
            default       -> planPipeline(model);     // "plan" 及未知 preset
        };
    }

    // ── 预定义 Pipeline 组合 ──────────────────────────────────────────

    /**
     * smart pipeline — 全套四阶段，适合复杂/探索性任务。
     * Phase 1: LlmProblemDecomposer（带降级）
     * Phase 2: TotStrategyPlanner（多路径 + 评分）
     * Phase 4: MetaCognitiveMonitor（停滞/置信度/资源/矛盾）
     */
    public static AutonomousPipeline smartPipeline(ChatModel model, AutonomousSpec config) {
        int tokenBudget = config.getMaxTokenBudget() > 0
                ? config.getMaxTokenBudget() : DEFAULT_TOKEN_BUDGET;
        return AutonomousPipeline.builder()
                .decomposer(new LlmProblemDecomposer(model))
                .strategyPlanner(new TotStrategyPlanner(model))
                .monitor(new MetaCognitiveMonitor(tokenBudget, config.getMaxTimeMs()))
                .build();
    }

    /**
     * plan pipeline — 中等成本，兼容现有 plan 模式行为。
     * Phase 1: LlmProblemDecomposer（成功标准注入）
     * Phase 2: LinearStrategyPlanner（单路径，对应原 PlannerEngine）
     * Phase 4: BasicStagnationMonitor（仅停滞检测）
     */
    public static AutonomousPipeline planPipeline(ChatModel model) {
        return AutonomousPipeline.builder()
                .decomposer(new LlmProblemDecomposer(model))
                .strategyPlanner(new LinearStrategyPlanner(model))
                .monitor(new BasicStagnationMonitor())
                .build();
    }

    /**
     * fast pipeline — 极低成本，适合简单/明确任务。
     * Phase 1: DefaultProblemDecomposer（启发式，零 LLM）
     * Phase 2: LinearStrategyPlanner（单路径）
     * Phase 4: BasicStagnationMonitor（仅停滞检测）
     */
    public static AutonomousPipeline fastPipeline(ChatModel model) {
        return AutonomousPipeline.builder()
                .decomposer(new DefaultProblemDecomposer())
                .strategyPlanner(new LinearStrategyPlanner(model))
                .monitor(new BasicStagnationMonitor())
                .build();
    }
}
