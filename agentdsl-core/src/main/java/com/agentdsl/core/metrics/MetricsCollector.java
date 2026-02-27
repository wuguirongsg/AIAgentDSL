package com.agentdsl.core.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 工具执行指标聚合服务。
 *
 * <p>
 * 线程安全的单例。所有工具调用的 {@link ToolMetrics} 都汇聚到此，
 * 上层可通过 API 查询统计数据。
 *
 * <p>
 * 当前实现为 <b>纯内存存储</b>，重启后历史数据清空。
 *
 * <p>
 * <b>扩展点（未来可替换或增强）：</b>
 * 
 * <pre>
 *   TODO [METRICS-EXT-4] SPI 扩展 — 允许用户注册自定义 MetricsExporter：
 *       ServiceLoader.load(MetricsExporter.class).forEach(exp -> exp.export(metrics));
 *       可实现：写数据库、推 Prometheus pushgateway、发 Kafka 等
 *
 *   TODO [METRICS-EXT-5] 对接 Micrometer MeterRegistry（需引入 micrometer-core 依赖）：
 *       registry.counter("agentdsl.tool.calls", "tool", toolName, "result",
 *                        success ? "success" : "failure").increment();
 *       registry.timer("agentdsl.tool.duration", "tool", toolName)
 *               .record(executionTimeMs, TimeUnit.MILLISECONDS);
 *
 *   TODO [METRICS-EXT-6] 容量限制 — 当列表超过阈值时自动清理最旧数据，避免内存泄漏：
 *       private static final int MAX_RECORDS = 10_000;
 *
 *   TODO [METRICS-EXT-7] 持久化查询 — 可通过 MetricsRepository 将数据写入
 *       InfluxDB / TimescaleDB / 本地 SQLite，实现历史趋势查询
 * </pre>
 *
 * <h3>使用示例</h3>
 * 
 * <pre>{@code
 * MetricsCollector metrics = MetricsCollector.getInstance();
 *
 * // 查询 http_get 工具的统计
 * long callCount = metrics.getToolCallCount("http_get");
 * double avgMs = metrics.getAverageExecutionTime("http_get");
 * double success = metrics.getSuccessRate("http_get");
 *
 * // 获取所有工具的调用次数排行
 * Map<String, Long> counts = metrics.getToolCallCounts();
 * }</pre>
 */
public class MetricsCollector {

    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }

    // -----------------------------------------------------------------------
    // Internal state — ConcurrentLinkedDeque 保证并发安全与高性能
    // -----------------------------------------------------------------------

    /**
     * 默认最大保留的指标记录条数，防止内存溢出 (OOM)
     */
    private static final int MAX_RECORDS = 10_000;

    /**
     * 所有工具执行指标列表（滑动窗口）
     */
    private final ConcurrentLinkedDeque<ToolMetrics> records = new ConcurrentLinkedDeque<>();

    /**
     * 由于 ConcurrentLinkedDeque.size() 是 O(n) 操作，维护一个近似的计数器优化性能
     */
    private final AtomicInteger sizeCounter = new AtomicInteger(0);

    private MetricsCollector() {
    }

    // -----------------------------------------------------------------------
    // 写入
    // -----------------------------------------------------------------------

    /**
     * 记录一次工具执行指标。
     *
     * <p>
     * 采用滑动窗口模式：如果容量达到 {@link #MAX_RECORDS}，则移除最旧的一条记录。
     *
     * <p>
     * TODO [METRICS-EXT-4] 在此处触发 SPI 导出，将指标推送给外部观测系统（如 SQLite / Prometheus）
     */
    public void record(ToolMetrics metrics) {
        records.addLast(metrics);
        if (sizeCounter.incrementAndGet() > MAX_RECORDS) {
            records.pollFirst(); // 淘汰最老数据
            sizeCounter.decrementAndGet();
        }
    }

    // -----------------------------------------------------------------------
    // 读取 / 统计
    // -----------------------------------------------------------------------

    /**
     * 获取所有指标的快照（转换为 List）。
     */
    public List<ToolMetrics> getAllMetrics() {
        return List.copyOf(records);
    }

    /**
     * 获取指定工具的所有指标。
     */
    public List<ToolMetrics> getToolMetrics(String toolName) {
        return records.stream()
                .filter(m -> toolName.equals(m.getToolName()))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定工具的总调用次数。
     */
    public long getToolCallCount(String toolName) {
        return records.stream()
                .filter(m -> toolName.equals(m.getToolName()))
                .count();
    }

    /**
     * 获取指定工具的平均执行耗时（毫秒）。
     *
     * @return 平均耗时，若无调用记录则返回 0.0
     */
    public double getAverageExecutionTime(String toolName) {
        return records.stream()
                .filter(m -> toolName.equals(m.getToolName()))
                .mapToLong(ToolMetrics::getExecutionTimeMs)
                .average()
                .orElse(0.0);
    }

    /**
     * 获取指定工具的成功率（0.0 ~ 1.0）。
     *
     * @return 成功率，若无调用记录则返回 0.0
     */
    public double getSuccessRate(String toolName) {
        List<ToolMetrics> toolRecords = getToolMetrics(toolName);
        if (toolRecords.isEmpty())
            return 0.0;
        long successCount = toolRecords.stream().filter(ToolMetrics::isSuccess).count();
        return (double) successCount / toolRecords.size();
    }

    /**
     * 获取所有工具的调用次数汇总，按调用次数降序排序。
     *
     * @return Map&lt;toolName, callCount&gt;
     */
    public Map<String, Long> getToolCallCounts() {
        return records.stream()
                .collect(Collectors.groupingBy(ToolMetrics::getToolName, Collectors.counting()));
    }

    /**
     * 获取当前滑动窗口内的总调用次数。
     */
    public long getTotalCallCount() {
        return sizeCounter.get();
    }

    /**
     * 获取总体成功率（所有工具合并计算）。
     *
     * @return 成功率（0.0 ~ 1.0），若无调用记录则返回 0.0
     */
    public double getOverallSuccessRate() {
        if (records.isEmpty())
            return 0.0;
        long successCount = records.stream().filter(ToolMetrics::isSuccess).count();
        return (double) successCount / records.size();
    }

    /**
     * 清空所有指标记录（通常在测试或引擎重启场景使用）。
     */
    public void clear() {
        records.clear();
        sizeCounter.set(0);
    }
}
