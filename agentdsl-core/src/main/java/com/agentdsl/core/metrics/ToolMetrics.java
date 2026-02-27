package com.agentdsl.core.metrics;

/**
 * 工具执行指标快照。
 *
 * <p>
 * 每次工具调用结束后（无论成功失败）自动采集并交给 {@link MetricsCollector} 聚合。
 *
 * <p>
 * <b>扩展点（未来可对接外部监控系统）：</b>
 * 
 * <pre>
 *   TODO [METRICS-EXT-1] 对接 Micrometer：
 *       MetricRegistry.counter("agentdsl.tool.calls", "tool", toolName).increment();
 *       MetricRegistry.timer("agentdsl.tool.duration", "tool", toolName).record(durationMs, MILLISECONDS);
 *
 *   TODO [METRICS-EXT-2] 对接 OpenTelemetry Span：
 *       Span span = tracer.spanBuilder("tool." + toolName).startSpan();
 *       span.setAttribute("tool.success", success);
 *       span.end();
 *
 *   TODO [METRICS-EXT-3] 对接持久化存储（如写入数据库或时序数据库 InfluxDB / Prometheus pushgateway）：
 *       MetricsPersistence.persist(this);
 * </pre>
 */
public class ToolMetrics {

    /** 工具名称 */
    private final String toolName;

    /** 执行耗时（毫秒） */
    private final long executionTimeMs;

    /** 是否成功 */
    private final boolean success;

    /**
     * 错误类型（成功时为 null）。
     * 例如 "TimeoutException", "ValidationError", "RuntimeException"
     */
    private final String errorType;

    /** 传入的参数数量 */
    private final int paramCount;

    /** 返回值的字符串长度（-1 表示无返回值） */
    private final int responseLength;

    /** 采集时间戳（epoch millis） */
    private final long timestamp;

    public ToolMetrics(String toolName, long executionTimeMs, boolean success,
            String errorType, int paramCount, int responseLength) {
        this.toolName = toolName;
        this.executionTimeMs = executionTimeMs;
        this.success = success;
        this.errorType = errorType;
        this.paramCount = paramCount;
        this.responseLength = responseLength;
        this.timestamp = System.currentTimeMillis();
    }

    public String getToolName() {
        return toolName;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorType() {
        return errorType;
    }

    public int getParamCount() {
        return paramCount;
    }

    public int getResponseLength() {
        return responseLength;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "ToolMetrics{tool='" + toolName + "', durationMs=" + executionTimeMs
                + ", success=" + success + ", errorType=" + errorType
                + ", paramCount=" + paramCount + ", responseLength=" + responseLength + "}";
    }
}
