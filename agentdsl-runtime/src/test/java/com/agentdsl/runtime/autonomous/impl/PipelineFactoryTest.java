package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.core.spec.AutonomousSpec;
import com.agentdsl.runtime.StubChatModel;
import com.agentdsl.runtime.autonomous.pipeline.AutonomousPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PipelineFactory")
class PipelineFactoryTest {

    private final StubChatModel model = new StubChatModel().addTextResponse("{}");

    @Test
    @DisplayName("smart preset 组装 Llm + ToT + MetaCognitive")
    void smartPreset() {
        AutonomousSpec spec = new AutonomousSpec();
        spec.setPreset("smart");
        spec.setMaxTokenBudget(50_000);

        AutonomousPipeline p = PipelineFactory.create(spec, model);

        assertInstanceOf(LlmProblemDecomposer.class, p.getDecomposer());
        assertInstanceOf(TotStrategyPlanner.class, p.getStrategyPlanner());
        assertInstanceOf(MetaCognitiveMonitor.class, p.getMonitor());
    }

    @Test
    @DisplayName("plan preset 组装 Llm + Linear + Basic")
    void planPreset() {
        AutonomousSpec spec = new AutonomousSpec();
        spec.setPreset("plan");

        AutonomousPipeline p = PipelineFactory.create(spec, model);

        assertInstanceOf(LlmProblemDecomposer.class, p.getDecomposer());
        assertInstanceOf(LinearStrategyPlanner.class, p.getStrategyPlanner());
        assertInstanceOf(BasicStagnationMonitor.class, p.getMonitor());
    }

    @Test
    @DisplayName("fast preset 组装 Default + Linear + Basic")
    void fastPreset() {
        AutonomousSpec spec = new AutonomousSpec();
        spec.setPreset("fast");

        AutonomousPipeline p = PipelineFactory.create(spec, model);

        assertInstanceOf(DefaultProblemDecomposer.class, p.getDecomposer());
        assertInstanceOf(LinearStrategyPlanner.class, p.getStrategyPlanner());
        assertInstanceOf(BasicStagnationMonitor.class, p.getMonitor());
    }

    @Test
    @DisplayName("未知 preset 回退到 plan 组合")
    void unknownPresetDefaultsToPlan() {
        AutonomousSpec spec = new AutonomousSpec();
        spec.setPreset("unknown");

        AutonomousPipeline p = PipelineFactory.create(spec, model);

        assertInstanceOf(LlmProblemDecomposer.class, p.getDecomposer());
        assertInstanceOf(LinearStrategyPlanner.class, p.getStrategyPlanner());
    }
}
