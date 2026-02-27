package com.agentdsl.runtime.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工作流整体执行追踪，包含所有步骤的 {@link StepTrace}。
 *
 * <p>
 * 工作流执行完毕后可通过 {@code WorkflowResult.getExecutionTrace()} 获取。
 *
 * <p>
 * <b>扩展点（未来对接外部观测平台）：</b>
 * 
 * <pre>
 *   TODO [TRACE-EXT-3] 对接 OpenTelemetry Span 树：
 *       ExecutionTrace → 根 Span，每个 StepTrace → 子 Span
 *       上报到 Jaeger / Zipkin / Grafana Tempo / SkyWalking
 *       实现方式：引入 opentelemetry-sdk，在 WorkflowExecutor 的 execute() 开始/结束时
 *       创建/结束 Span，并在每个 executeSequential/executeParallel 中添加子 Span
 *
 *   TODO [TRACE-EXT-4] 持久化查询 — 可将 ExecutionTrace 序列化（JSON）写入：
 *       - 本地文件（.agentdsl/traces/<workflowName>/<timestamp>.json）
 *       - 关系型数据库（表：workflow_traces, step_traces）
 *       - 时序数据库（InfluxDB / TimescaleDB）
 *
 *   TODO [TRACE-EXT-5] 仪表盘集成 — 将数据推送到 Grafana 数据源后可实现：
 *       - 工作流 P50/P95/P99 耗时面板
 *       - 步骤耗时分布热力图
 *       - 失败率趋势告警
 * </pre>
 *
 * <h3>使用示例</h3>
 * 
 * <pre>{@code
 * WorkflowResult result = engine.executeWorkflow("translate-pipeline", "Hello World");
 * ExecutionTrace trace = result.getExecutionTrace();
 *
 * System.out.println("总耗时: " + trace.getTotalDurationMs() + "ms");
 * System.out.println("状态: " + trace.getStatus());
 * trace.getSteps().forEach(
 *         step -> System.out.printf("  [%s] %s: %dms%n", step.getStatus(), step.getStepName(), step.getDurationMs()));
 * }</pre>
 */
public class ExecutionTrace {

    /** 工作流名称 */
    private final String workflowName;

    /** 所有步骤追踪（按执行顺序） */
    private final List<StepTrace> steps = new ArrayList<>();

    /** 工作流开始时间戳（epoch millis） */
    private final long startTimestamp;

    /** 工作流总耗时（毫秒），执行完毕后填充 */
    private long totalDurationMs = -1;

    /**
     * 工作流最终状态：
     * <ul>
     * <li>{@code "completed"} — 全部步骤正常完成</li>
     * <li>{@code "failed"} — 某步骤抛出异常导致工作流终止</li>
     * </ul>
     *
     * TODO [TRACE-EXT-6] 补充 "timeout" 状态 — 当工作流级别超时机制实现后使用
     */
    private String status = "running";

    public ExecutionTrace(String workflowName) {
        this.workflowName = workflowName;
        this.startTimestamp = System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------
    // 写入（由 WorkflowExecutor 内部调用）
    // -----------------------------------------------------------------------

    /**
     * 追加一个步骤追踪记录。
     */
    public void addStep(StepTrace step) {
        steps.add(step);
    }

    /**
     * 标记工作流执行完毕并记录最终状态。
     *
     * @param status 最终状态 ("completed" / "failed")
     */
    public void complete(String status) {
        this.totalDurationMs = System.currentTimeMillis() - startTimestamp;
        this.status = status;
    }

    // -----------------------------------------------------------------------
    // 读取
    // -----------------------------------------------------------------------

    public String getWorkflowName() {
        return workflowName;
    }

    public List<StepTrace> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public String getStatus() {
        return status;
    }

    /** 获取成功完成的步骤数量。 */
    public long getCompletedStepCount() {
        return steps.stream().filter(s -> "completed".equals(s.getStatus())).count();
    }

    /** 获取失败的步骤数量。 */
    public long getFailedStepCount() {
        return steps.stream().filter(s -> "failed".equals(s.getStatus())).count();
    }

    @Override
    public String toString() {
        return "ExecutionTrace{workflow='" + workflowName
                + "', steps=" + steps.size()
                + ", totalDurationMs=" + totalDurationMs
                + ", status='" + status + "'}";
    }
}
