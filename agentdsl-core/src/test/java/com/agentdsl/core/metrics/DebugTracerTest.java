package com.agentdsl.core.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DebugTracerTest {

    @BeforeEach
    @AfterEach
    void resetTracer() {
        DebugTracer.disable();
    }

    @Test
    void testEnableDisable() {
        assertFalse(DebugTracer.isEnabled());

        DebugTracer.enable();
        assertTrue(DebugTracer.isEnabled());

        DebugTracer.disable();
        assertFalse(DebugTracer.isEnabled());
    }

    @Test
    void testRecordEvent() {
        DebugTracer.enable();

        DebugTracer.record(DebugEvent.Type.AGENT_START, "TestAgent", Map.of("key", "value"));

        List<DebugEvent> events = DebugTracer.getEvents();
        assertEquals(1, events.size());

        DebugEvent event = events.get(0);
        assertEquals(DebugEvent.Type.AGENT_START, event.getType());
        assertEquals("TestAgent", event.getSource());
        assertEquals("value", event.getDetails().get("key"));
        assertEquals(0, event.getDepth()); // Default depth
    }

    @Test
    void testZeroOverheadWhenDisabled() {
        DebugTracer.disable(); // Ensure disabled

        // This should not be recorded
        DebugTracer.record(DebugEvent.Type.AGENT_START, "TestAgent", Map.of("key", "value"));

        List<DebugEvent> events = DebugTracer.getEvents();
        assertTrue(events.isEmpty());
    }

    @Test
    void testDepthTracking() {
        DebugTracer.enable();

        DebugTracer.record(DebugEvent.Type.WORKFLOW_START, "WF", null); // depth 0

        DebugTracer.enter();
        DebugTracer.record(DebugEvent.Type.WORKFLOW_STEP_START, "Step1", null); // depth 1

        DebugTracer.enter();
        DebugTracer.record(DebugEvent.Type.AGENT_START, "Agent1", null); // depth 2

        DebugTracer.exit();
        DebugTracer.record(DebugEvent.Type.WORKFLOW_STEP_END, "Step1", null); // depth 1

        DebugTracer.exit();
        DebugTracer.record(DebugEvent.Type.WORKFLOW_END, "WF", null); // depth 0

        List<DebugEvent> events = DebugTracer.getEvents();
        assertEquals(5, events.size());

        assertEquals(0, events.get(0).getDepth()); // WORKFLOW_START
        assertEquals(1, events.get(1).getDepth()); // WORKFLOW_STEP_START
        assertEquals(2, events.get(2).getDepth()); // AGENT_START
        assertEquals(1, events.get(3).getDepth()); // WORKFLOW_STEP_END
        assertEquals(0, events.get(4).getDepth()); // WORKFLOW_END
    }

    @Test
    void testThreadLocalIsolation() throws InterruptedException {
        // Main thread
        DebugTracer.enable();
        DebugTracer.record(DebugEvent.Type.AGENT_START, "MainThread", null);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean childThreadVerified = new AtomicBoolean(false);

        // Child thread
        Thread childThread = new Thread(() -> {
            // Should be disabled in child thread initially
            if (!DebugTracer.isEnabled()) {
                DebugTracer.enable();
                DebugTracer.record(DebugEvent.Type.AGENT_START, "ChildThread", null);

                List<DebugEvent> childEvents = DebugTracer.getEvents();
                if (childEvents.size() == 1 && "ChildThread".equals(childEvents.get(0).getSource())) {
                    childThreadVerified.set(true);
                }
                DebugTracer.disable();
            }
            latch.countDown();
        });

        childThread.start();
        latch.await();

        // Verify child thread state was correct
        assertTrue(childThreadVerified.get());

        // Main thread should not see child thread events
        List<DebugEvent> mainEvents = DebugTracer.getEvents();
        assertEquals(1, mainEvents.size());
        assertEquals("MainThread", mainEvents.get(0).getSource());
    }
}
