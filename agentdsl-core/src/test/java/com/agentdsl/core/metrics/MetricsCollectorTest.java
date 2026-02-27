package com.agentdsl.core.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsCollector 单元测试。
 */
class MetricsCollectorTest {

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = MetricsCollector.getInstance();
        collector.clear(); // 每个测试前清空状态
    }

    @Test
    @DisplayName("记录成功的工具执行指标")
    void shouldRecordSuccessMetrics() {
        collector.record(new ToolMetrics("http_get", 120, true, null, 2, 500));
        collector.record(new ToolMetrics("http_get", 80, true, null, 2, 300));

        assertEquals(2, collector.getToolCallCount("http_get"));
        assertEquals(100.0, collector.getAverageExecutionTime("http_get"), 0.01);
        assertEquals(1.0, collector.getSuccessRate("http_get"), 0.001);
    }

    @Test
    @DisplayName("记录失败的工具执行指标")
    void shouldRecordFailureMetrics() {
        collector.record(new ToolMetrics("http_post", 50, true, null, 1, 200));
        collector.record(new ToolMetrics("http_post", 30010, false, "TimeoutException", 1, 0));

        assertEquals(2, collector.getToolCallCount("http_post"));
        assertEquals(0.5, collector.getSuccessRate("http_post"), 0.001);
    }

    @Test
    @DisplayName("按工具名称过滤指标")
    void shouldFilterByToolName() {
        collector.record(new ToolMetrics("http_get", 100, true, null, 1, 100));
        collector.record(new ToolMetrics("json_parse", 20, true, null, 1, 50));
        collector.record(new ToolMetrics("http_get", 150, false, "RuntimeException", 1, 0));

        assertEquals(2, collector.getToolMetrics("http_get").size());
        assertEquals(1, collector.getToolMetrics("json_parse").size());
        assertEquals(0, collector.getToolMetrics("file_read").size());
    }

    @Test
    @DisplayName("获取所有工具调用次数汇总")
    void shouldGetToolCallCounts() {
        collector.record(new ToolMetrics("http_get", 100, true, null, 1, 100));
        collector.record(new ToolMetrics("http_get", 200, true, null, 1, 200));
        collector.record(new ToolMetrics("json_parse", 20, true, null, 1, 50));

        var counts = collector.getToolCallCounts();
        assertEquals(2L, counts.get("http_get"));
        assertEquals(1L, counts.get("json_parse"));
    }

    @Test
    @DisplayName("全局成功率计算")
    void shouldCalculateOverallSuccessRate() {
        collector.record(new ToolMetrics("tool-a", 100, true, null, 1, 100));
        collector.record(new ToolMetrics("tool-b", 100, true, null, 1, 100));
        collector.record(new ToolMetrics("tool-c", 100, false, "Error", 1, 0));

        // 2/3 ≈ 0.667
        assertEquals(2.0 / 3.0, collector.getOverallSuccessRate(), 0.001);
        assertEquals(3, collector.getTotalCallCount());
    }

    @Test
    @DisplayName("无调用记录时返回 0")
    void shouldReturnZeroWhenNoRecords() {
        assertEquals(0, collector.getToolCallCount("unknown-tool"));
        assertEquals(0.0, collector.getAverageExecutionTime("unknown-tool"), 0.001);
        assertEquals(0.0, collector.getSuccessRate("unknown-tool"), 0.001);
        assertEquals(0.0, collector.getOverallSuccessRate(), 0.001);
    }

    @Test
    @DisplayName("clear() 清空所有记录")
    void shouldClearAllRecords() {
        collector.record(new ToolMetrics("http_get", 100, true, null, 1, 100));
        collector.clear();
        assertEquals(0, collector.getTotalCallCount());
        assertTrue(collector.getAllMetrics().isEmpty());
    }
}
