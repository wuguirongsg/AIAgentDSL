package com.agentdsl.runtime.autonomous.pipeline;

import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * Phase 1 接口：问题解构器。
 * 负责在任何规划和执行开始之前，对用户目标做结构化分析。
 *
 * <p>内置实现：
 * <ul>
 *   <li>{@code LlmProblemDecomposer} — 调用 LLM 生成 JSON 分析，带降级</li>
 *   <li>{@code DefaultProblemDecomposer} — 零 LLM 成本，返回简单默认规格</li>
 * </ul>
 */
public interface ProblemDecomposer {

    /**
     * 对用户目标进行结构化解构。
     *
     * @param userGoal       用户原始目标（自然语言）
     * @param availableTools 当前可用工具列表
     * @return 结构化的问题规格，实现必须保证永不返回 null（降级为 defaultSpec）
     */
    ProblemSpec decompose(String userGoal, List<ToolSpecification> availableTools);
}
