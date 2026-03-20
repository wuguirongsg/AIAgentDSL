package com.agentdsl.runtime.autonomous.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProblemSpec")
class ProblemSpecTest {

    @Test
    @DisplayName("recommendPreset 按任务类型映射")
    void recommendPresetByTaskType() {
        assertEquals("fast", specWithType(ProblemSpec.TaskType.SINGLE_STEP).recommendPreset());
        assertEquals("plan", specWithType(ProblemSpec.TaskType.MULTI_STEP).recommendPreset());
        assertEquals("smart", specWithType(ProblemSpec.TaskType.EXPLORATORY).recommendPreset());
        assertEquals("smart", specWithType(ProblemSpec.TaskType.OPEN_ENDED).recommendPreset());
    }

    private static ProblemSpec specWithType(ProblemSpec.TaskType type) {
        return ProblemSpec.builder("g")
                .taskType(type)
                .complexity(ProblemSpec.ComplexityLevel.MEDIUM)
                .successCriteria(List.of("ok"))
                .estimatedSteps(3)
                .build();
    }
}
