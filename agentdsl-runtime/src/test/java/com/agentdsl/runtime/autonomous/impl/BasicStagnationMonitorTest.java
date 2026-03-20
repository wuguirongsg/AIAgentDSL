package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.MonitorSignal;
import com.agentdsl.runtime.autonomous.model.StepContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BasicStagnationMonitor")
class BasicStagnationMonitorTest {

    private static StepContext stepWithTool(String name, int argsHash) {
        return new StepContext(
                "",
                List.of(new StepContext.ToolCall(name, argsHash, "ok")),
                0);
    }

    @Test
    @DisplayName("NO_TOOL 不计入停滞")
    void noToolIsHealthy() {
        BasicStagnationMonitor m = new BasicStagnationMonitor();
        for (int i = 0; i < 5; i++) {
            MonitorSignal s = m.analyze(new StepContext("思考中", List.of(), 0));
            assertTrue(s.isHealthy(), "step " + i);
        }
    }

    @Test
    @DisplayName("连续三步相同指纹触发 STRATEGY_SWITCH")
    void stagnationTriggersSwitch() {
        BasicStagnationMonitor m = new BasicStagnationMonitor();
        assertTrue(m.analyze(stepWithTool("t", 1)).isHealthy());
        assertTrue(m.analyze(stepWithTool("t", 1)).isHealthy());
        MonitorSignal s = m.analyze(stepWithTool("t", 1));
        assertTrue(s.requiresIntervention());
        assertTrue(s.hasType(MonitorSignal.InterventionType.STRATEGY_SWITCH));
        assertEquals(MonitorSignal.Severity.HIGH, s.highestSeverity());
    }

    @Test
    @DisplayName("reset 清空停滞窗口")
    void resetClearsWindow() {
        BasicStagnationMonitor m = new BasicStagnationMonitor();
        m.analyze(stepWithTool("t", 1));
        m.analyze(stepWithTool("t", 1));
        m.reset();
        assertTrue(m.analyze(stepWithTool("t", 1)).isHealthy());
        assertTrue(m.analyze(stepWithTool("t", 1)).isHealthy());
    }
}
