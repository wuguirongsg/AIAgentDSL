package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.StubChatModel;
import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmProblemDecomposer")
class LlmProblemDecomposerTest {

    @Test
    @DisplayName("合法 JSON 解析为 ProblemSpec")
    void parsesValidJson() {
        String json = """
                {"taskType":"single_step","complexity":"simple","constraints":[],"successCriteria":["验收通过"],
                "uncertainties":[],"requiredTools":[],"missingCapabilities":[],"estimatedSteps":2,"decomposedSubGoals":[]}""";
        StubChatModel model = new StubChatModel().addTextResponse(json);
        LlmProblemDecomposer dec = new LlmProblemDecomposer(model);

        ProblemSpec spec = dec.decompose("目标", List.of());

        assertEquals(ProblemSpec.TaskType.SINGLE_STEP, spec.getTaskType());
        assertEquals(ProblemSpec.ComplexityLevel.SIMPLE, spec.getComplexity());
        assertEquals(List.of("验收通过"), spec.getSuccessCriteria());
        assertEquals(2, spec.getEstimatedSteps());
    }

    @Test
    @DisplayName("非法 JSON 降级为 DefaultProblemDecomposer")
    void invalidJsonFallsBack() {
        StubChatModel model = new StubChatModel().addTextResponse("这不是 JSON");
        LlmProblemDecomposer dec = new LlmProblemDecomposer(model);
        DefaultProblemDecomposer fallback = new DefaultProblemDecomposer();

        ProblemSpec spec = dec.decompose("单句目标", List.of());
        ProblemSpec expected = fallback.decompose("单句目标", List.of());

        assertEquals(expected.getTaskType(), spec.getTaskType());
        assertEquals(expected.getOriginalGoal(), spec.getOriginalGoal());
    }
}
