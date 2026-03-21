package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.core.spec.AutonomousSpec;
import com.agentdsl.runtime.autonomous.pipeline.AutonomousPipeline;
import com.agentdsl.runtime.autonomous.pipeline.PipelineThoughtListener;
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
        return create(config, model, null);
    }

    /**
     * 根据 AutonomousSpec 创建对应的 Pipeline（带思考过程监听器）。
     *
     * @param config Agent 的自主配置
     * @param model  ChatModel 实例（LLM 型组件需要）
     * @param thoughtListener 思考过程监听器（可选，null 表示不输出）
     * @return 组装好的 Pipeline
     */
    public static AutonomousPipeline create(AutonomousSpec config, ChatModel model,
                                            PipelineThoughtListener thoughtListener) {
        String preset = config.getPreset();

        return switch (preset.toLowerCase()) {
            case "smart"  -> smartPipeline(model, config, thoughtListener);
            case "fast"   -> fastPipeline(model, thoughtListener);
            default       -> planPipeline(model, thoughtListener);     // "plan" 及未知 preset
        };
    }

    // ── 预定义 Pipeline 组合 ──────────────────────────────────────────

    public static AutonomousPipeline smartPipeline(ChatModel model, AutonomousSpec config) {
        return smartPipeline(model, config, null);
    }

    public static AutonomousPipeline smartPipeline(ChatModel model, AutonomousSpec config,
                                                   PipelineThoughtListener thoughtListener) {
        int tokenBudget = config.getMaxTokenBudget() > 0
                ? config.getMaxTokenBudget() : DEFAULT_TOKEN_BUDGET;
        return AutonomousPipeline.builder()
                .decomposer(new LlmProblemDecomposer(model))
                .strategyPlanner(new TotStrategyPlanner(model))
                .monitor(new MetaCognitiveMonitor(tokenBudget, config.getMaxTimeMs()))
                .thoughtListener(thoughtListener)
                .build();
    }

    public static AutonomousPipeline planPipeline(ChatModel model) {
        return planPipeline(model, null);
    }

    public static AutonomousPipeline planPipeline(ChatModel model,
                                                  PipelineThoughtListener thoughtListener) {
        return AutonomousPipeline.builder()
                .decomposer(new LlmProblemDecomposer(model))
                .strategyPlanner(new LinearStrategyPlanner(model))
                .monitor(new BasicStagnationMonitor())
                .thoughtListener(thoughtListener)
                .build();
    }

    public static AutonomousPipeline fastPipeline(ChatModel model) {
        return fastPipeline(model, null);
    }

    public static AutonomousPipeline fastPipeline(ChatModel model,
                                                  PipelineThoughtListener thoughtListener) {
        return AutonomousPipeline.builder()
                .decomposer(new DefaultProblemDecomposer())
                .strategyPlanner(new LinearStrategyPlanner(model))
                .monitor(new BasicStagnationMonitor())
                .thoughtListener(thoughtListener)
                .build();
    }
}
