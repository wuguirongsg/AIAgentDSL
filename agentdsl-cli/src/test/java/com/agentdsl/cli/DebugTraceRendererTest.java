package com.agentdsl.cli;

import com.agentdsl.core.metrics.DebugEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugTraceRendererTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    void testRenderEmptyList() {
        DebugTraceRenderer.render(List.of());
        assertTrue(outContent.toString().isEmpty());
    }

    @Test
    void testRenderWorkflowStart() {
        DebugEvent event = new DebugEvent(
                DebugEvent.Type.WORKFLOW_START,
                "test-flow",
                Map.of("input", "Hello"),
                0);

        DebugTraceRenderer.render(List.of(event));

        String output = outContent.toString();
        assertTrue(output.contains("DEBUG TRACE"));
        assertTrue(output.contains("WORKFLOW_START [test-flow]"));
        assertTrue(output.contains("input: Hello"));
    }

    @Test
    void testRenderModelRequestWithMessages() {
        DebugEvent event = new DebugEvent(
                DebugEvent.Type.MODEL_REQUEST,
                "agent1",
                Map.of(
                        "iteration", 0,
                        "messages", List.of(
                                Map.of("type", "SYSTEM", "text", "You are a helpful assistant."),
                                Map.of("type", "USER", "text", "Hi there!"))),
                1);

        DebugTraceRenderer.render(List.of(event));

        String output = outContent.toString();
        assertTrue(output.contains("MODEL_REQUEST"));
        assertTrue(output.contains("iteration: 0"));
        assertTrue(output.contains("messages:"));
        assertTrue(output.contains("- [SYSTEM]"));
        assertTrue(output.contains("You are a helpful assistant."));
        assertTrue(output.contains("- [USER]"));
        assertTrue(output.contains("Hi there!"));
    }

    @Test
    void testRenderMultiLineOutput() {
        DebugEvent event = new DebugEvent(
                DebugEvent.Type.MODEL_RESPONSE,
                "agent1",
                Map.of("text", "Line 1\nLine 2\nLine 3", "durationMs", 150L),
                1);

        DebugTraceRenderer.render(List.of(event));

        String output = outContent.toString();
        assertTrue(output.contains("MODEL_RESPONSE (150ms)"));
        assertTrue(output.contains("text:"));
        assertTrue(output.contains("Line 1"));
        assertTrue(output.contains("Line 2"));
        assertTrue(output.contains("Line 3"));
        // Make sure durationMs is not duplicated in details
        assertTrue(output.indexOf("durationMs:") == -1);
    }
}
