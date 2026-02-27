package com.agentdsl.runtime.metrics;

/**
 * 工作流单个步骤的执行追踪记录。
 *
 * <p>
 * <b>扩展点：</b>
 * 
 * <pre>
 *   TODO [TRACE-EXT-1] 对接 OpenTelemetry Span：
 *       每个 StepTrace 对应一个子 Span，支持分布式追踪链路可视化（如 Jaeger / Zipkin / Tempo）
 *
 *   TODO [TRACE-EXT-2] 持久化查询：
 *       写入数据库后可按 workflowName + stepName + 时间范围查询历史执行情况
 * </pre>
 */
public class StepTrace {

    /** 步骤名称 */
    private final String stepName;

    /** 该步骤调用的 Agent 名称（条件/并行步骤可能为 null） */
    private final String agentName;

    /** 输入摘要（截断，最多 200 字符） */
    private final String inputSummary;

    /** 输出摘要（截断，最多 200 字符） */
    private final String outputSummary;

    /** 步骤执行耗时（毫秒） */
    private final long durationMs;

    /**
     * 步骤状态：
     * <ul>
     * <li>{@code "completed"} — 正常完成</li>
     * <li>{@code "failed"} — 执行抛出异常</li>
     * <li>{@code "skipped"} — 条件路由中未被选中的分支</li>
     * </ul>
     */
    private final String status;

    public StepTrace(String stepName, String agentName,
            String inputSummary, String outputSummary,
            long durationMs, String status) {
        this.stepName = stepName;
        this.agentName = agentName;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.durationMs = durationMs;
        this.status = status;
    }

    public String getStepName() {
        return stepName;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "StepTrace{step='" + stepName + "', agent='" + agentName
                + "', durationMs=" + durationMs + ", status='" + status + "'}";
    }
}
