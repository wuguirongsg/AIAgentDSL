package com.agentdsl.runtime.autonomous.pipeline;

import com.agentdsl.runtime.autonomous.impl.BasicStagnationMonitor;
import com.agentdsl.runtime.autonomous.impl.DefaultProblemDecomposer;
import com.agentdsl.runtime.autonomous.model.ExecutionStrategy;
import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutonomousPipeline")
class AutonomousPipelineTest {

    @Test
    @DisplayName("Builder 缺组件时 build 抛 IllegalStateException")
    void buildRequiresAllParts() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AutonomousPipeline.builder()
                        .decomposer(new DefaultProblemDecomposer())
                        .build());
        assertTrue(ex.getMessage().contains("requires"));
    }

    @Test
    @DisplayName("prepare 串联 Phase1+2 并 reset 监控器")
    void prepareRunsPhases() {
        AutonomousPipeline pipeline = AutonomousPipeline.builder()
                .decomposer(new DefaultProblemDecomposer())
                .strategyPlanner((ProblemSpec problem, List<ToolSpecification> tools) ->
                        ExecutionStrategy.empty())
                .monitor(new BasicStagnationMonitor())
                .build();

        List<ToolSpecification> tools = List.of(
                ToolSpecification.builder().name("x").description("d").build());
        PipelineContext ctx = pipeline.prepare("一句话目标", tools);

        assertNotNull(ctx.getProblemSpec());
        assertEquals("一句话目标", ctx.getProblemSpec().getOriginalGoal());
        assertNotNull(ctx.getExecutionStrategy());
    }
}
