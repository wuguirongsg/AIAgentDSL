package com.agentdsl.runtime;

import com.agentdsl.runtime.metrics.ExecutionTrace;

import java.util.Collections;
import java.util.Map;

/**
 * 工作流执行结果。
 *
 * <p>
 * 包含最终输出、所有步骤结果以及执行追踪信息 ({@link ExecutionTrace})。
 *
 * <p>
 * <b>可观测性扩展点：</b>
 * 
 * <pre>
 *   TODO [METRICS-EXT-7] 将 executionTrace 序列化并持久化，实现历史执行记录查询：
 *       repository.save(workflowName, executionTrace);
 *       后续可在 Sprint 7（Spring Boot Starter）中通过 REST API 暴露历史追踪列表
 *
 *   TODO [TRACE-EXT-5] 将 executionTrace 数据推送到 Grafana 数据源，实现仪表盘展示
 * </pre>
 */
public class WorkflowResult {

    /** 最终输出 */
    private final Object finalOutput;

    /** 所有步骤的命名结果 */
    private final Map<String, Object> stepResults;

    /**
     * 工作流执行追踪（含各步骤耗时、输入/输出摘要、状态）。
     * 可用于性能分析、调试和后续对接外部监控平台。
     */
    private final ExecutionTrace executionTrace;

    /** 向后兼容的两参数构造函数（executionTrace 为 null）。 */
    public WorkflowResult(Object finalOutput, Map<String, Object> stepResults) {
        this(finalOutput, stepResults, null);
    }

    public WorkflowResult(Object finalOutput, Map<String, Object> stepResults,
            ExecutionTrace executionTrace) {
        this.finalOutput = finalOutput;
        this.stepResults = Collections.unmodifiableMap(stepResults);
        this.executionTrace = executionTrace;
    }

    /**
     * 获取最终输出。
     */
    public Object getFinalOutput() {
        return finalOutput;
    }

    /**
     * 获取最终输出的字符串形式。
     */
    public String getFinalOutputAsString() {
        return finalOutput != null ? finalOutput.toString() : null;
    }

    /**
     * 获取所有步骤结果。
     */
    public Map<String, Object> getStepResults() {
        return stepResults;
    }

    /**
     * 获取指定步骤的结果。
     */
    public Object getStepResult(String stepName) {
        return stepResults.get(stepName);
    }

    /**
     * 获取执行追踪信息。
     *
     * <p>
     * 包含每个步骤的耗时、输入/输出摘要、状态，以及整体工作流的总耗时和状态。
     *
     * <p>
     * 如果工作流执行过程中未启用追踪（如使用了旧的两参数构造函数），此方法返回 {@code null}。
     *
     * @return {@link ExecutionTrace}，可能为 null
     */
    public ExecutionTrace getExecutionTrace() {
        return executionTrace;
    }

    @Override
    public String toString() {
        return "WorkflowResult{" +
                "finalOutput=" + (finalOutput != null ? finalOutput.toString().substring(0,
                        Math.min(100, finalOutput.toString().length())) : "null")
                +
                ", steps=" + stepResults.keySet() +
                ", trace=" + (executionTrace != null ? executionTrace.getStatus() : "none") +
                '}';
    }
}
