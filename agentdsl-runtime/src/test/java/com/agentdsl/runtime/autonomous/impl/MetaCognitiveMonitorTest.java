package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.MonitorSignal;
import com.agentdsl.runtime.autonomous.model.StepContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetaCognitiveMonitor")
class MetaCognitiveMonitorTest {

    private static StepContext step(String llmOut, List<StepContext.ToolCall> tools, int tokens) {
        return new StepContext(llmOut, tools, tokens);
    }

    @Test
    @DisplayName("置信度启发式推断")
    void inferConfidenceHeuristic() {
        MetaCognitiveMonitor m = new MetaCognitiveMonitor(10_000, 0);
        assertTrue(m.inferConfidence("任务已完成") > m.inferConfidence("不确定是否可行"));
    }

    @Test
    @DisplayName("Token 超过 80% 触发 COMPRESS 预警")
    void tokenWarning() {
        MetaCognitiveMonitor m = new MetaCognitiveMonitor(1_000, 0);
        MonitorSignal s = m.analyze(step("ok", List.of(), 850));
        assertTrue(s.requiresIntervention());
        assertTrue(s.hasType(MonitorSignal.InterventionType.COMPRESS_CONTEXT));
        assertEquals(MonitorSignal.Severity.LOW, s.getInterventions().stream()
                .filter(i -> i.type() == MonitorSignal.InterventionType.COMPRESS_CONTEXT)
                .findFirst()
                .orElseThrow()
                .severity());
    }

    @Test
    @DisplayName("Token 超过 95% 高严重度压缩")
    void tokenCritical() {
        MetaCognitiveMonitor m = new MetaCognitiveMonitor(1_000, 0);
        MonitorSignal s = m.analyze(step("ok", List.of(), 960));
        assertTrue(s.hasType(MonitorSignal.InterventionType.COMPRESS_CONTEXT));
        assertEquals(MonitorSignal.Severity.HIGH, s.getInterventions().stream()
                .filter(i -> i.type() == MonitorSignal.InterventionType.COMPRESS_CONTEXT)
                .findFirst()
                .orElseThrow()
                .severity());
    }

    @Test
    @DisplayName("连续三步置信度下降触发 REPLAN")
    void confidenceDeclineReplan() {
        MetaCognitiveMonitor m = new MetaCognitiveMonitor(0, 0);
        assertTrue(m.analyze(step("已完成成功", List.of(), 0)).isHealthy());
        assertTrue(m.analyze(step("明确", List.of(), 0)).isHealthy());
        MonitorSignal s = m.analyze(step("不确定", List.of(), 0));
        assertTrue(s.hasType(MonitorSignal.InterventionType.REPLAN));
    }

    @Test
    @DisplayName("reset 后预算状态清零")
    void resetClearsTokenAccumulation() {
        MetaCognitiveMonitor m = new MetaCognitiveMonitor(500, 0);
        m.analyze(step("", List.of(), 400));
        m.reset();
        MonitorSignal s = m.analyze(step("", List.of(), 400));
        assertTrue(s.isHealthy());
    }
}
