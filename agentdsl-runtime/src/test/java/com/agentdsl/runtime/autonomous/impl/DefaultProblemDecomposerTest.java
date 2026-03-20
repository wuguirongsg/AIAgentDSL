package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultProblemDecomposer")
class DefaultProblemDecomposerTest {

    private final DefaultProblemDecomposer decomposer = new DefaultProblemDecomposer();

    @Test
    @DisplayName("短目标视为单步，并收集工具名")
    void shortGoalSingleStep() {
        List<ToolSpecification> tools = List.of(
                ToolSpecification.builder().name("a").description("d").build(),
                ToolSpecification.builder().name("b").description("d").build());

        ProblemSpec spec = decomposer.decompose("做一件事", tools);

        assertEquals("做一件事", spec.getOriginalGoal());
        assertEquals(ProblemSpec.TaskType.SINGLE_STEP, spec.getTaskType());
        assertEquals(List.of("a", "b"), spec.getRequiredTools());
        assertFalse(spec.getSuccessCriteria().isEmpty());
    }

    @Test
    @DisplayName("多句目标视为多步")
    void multiSentenceMultiStep() {
        ProblemSpec spec = decomposer.decompose(
                "先做 A。再做 B。最后 C。", List.of());

        assertEquals(ProblemSpec.TaskType.MULTI_STEP, spec.getTaskType());
        assertTrue(spec.getEstimatedSteps() >= 3);
    }
}
