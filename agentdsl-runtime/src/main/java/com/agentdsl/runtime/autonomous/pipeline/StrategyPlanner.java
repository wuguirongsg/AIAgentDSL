package com.agentdsl.runtime.autonomous.pipeline;

import com.agentdsl.runtime.autonomous.model.ExecutionStrategy;
import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * Phase 2 接口：策略规划器。
 * 根据问题规格，生成一个或多条候选执行路径，评分并选出最优方案。
 *
 * <p>内置实现：
 * <ul>
 *   <li>{@code TotStrategyPlanner} — ToT 多路径候选 + 评分剪枝（smart preset）</li>
 *   <li>{@code LinearStrategyPlanner} — 单路径规划，兼容现有 PlannerEngine 行为（plan/fast preset）</li>
 * </ul>
 */
public interface StrategyPlanner {

    /**
     * 根据问题规格和可用工具，制定执行策略。
     *
     * @param problem        Phase 1 输出的问题规格
     * @param availableTools 当前可用工具列表
     * @return 最终执行策略（含主路径 + 可选备用路径）
     */
    ExecutionStrategy plan(ProblemSpec problem, List<ToolSpecification> availableTools);
}
