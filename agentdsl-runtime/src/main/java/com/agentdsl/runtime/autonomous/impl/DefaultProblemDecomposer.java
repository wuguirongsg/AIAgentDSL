package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import com.agentdsl.runtime.autonomous.pipeline.ProblemDecomposer;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * 默认问题解构器（零 LLM 成本）。
 * 不调用 LLM，直接返回 {@link ProblemSpec#defaultSpec(String)}；
 * 适用于 fast preset 或任何不需要精细问题分析的场景。
 */
public class DefaultProblemDecomposer implements ProblemDecomposer {

    @Override
    public ProblemSpec decompose(String userGoal, List<ToolSpecification> availableTools) {
        // 简单启发式：根据句子数量判断复杂度
        int sentenceCount = userGoal.split("[。？！\\.!?]").length;
        ProblemSpec.TaskType type = sentenceCount > 2 ? ProblemSpec.TaskType.MULTI_STEP
                                                       : ProblemSpec.TaskType.SINGLE_STEP;

        // 提取工具名作为 requiredTools 初始猜测
        List<String> toolNames = availableTools.stream()
                .map(ToolSpecification::name)
                .toList();

        return ProblemSpec.builder(userGoal)
                .taskType(type)
                .complexity(ProblemSpec.ComplexityLevel.MEDIUM)
                .successCriteria(List.of("用户目标得到满足，任务完成"))
                .requiredTools(toolNames)
                .estimatedSteps(Math.max(3, sentenceCount * 2))
                .build();
    }
}
