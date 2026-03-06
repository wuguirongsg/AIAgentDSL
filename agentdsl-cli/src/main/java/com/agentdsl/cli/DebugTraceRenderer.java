package com.agentdsl.cli;

import com.agentdsl.core.metrics.DebugEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 调试追踪日志渲染器。
 * 将收集到的 DebugEvent 列表格式化输出到控制台。
 */
public class DebugTraceRenderer {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    public static void render(List<DebugEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        System.out.println("\n🔍 ═══════════════ DEBUG TRACE ═══════════════\n");

        for (int i = 0; i < events.size(); i++) {
            DebugEvent event = events.get(i);
            renderEvent(event, isLastSibling(events, i));
        }

        System.out.println("\n🔍 ═══════════════════════════════════════════\n");
    }

    private static void renderEvent(DebugEvent event, boolean isLast) {
        String timeStr = TIME_FORMAT.format(new Date(event.getTimestamp()));
        String indent = "│  ".repeat(Math.max(0, event.getDepth()));
        String prefix = indent + (isLast ? "└─ " : "├─ ");

        if (event.getDepth() == 0) {
            prefix = "";
        }

        switch (event.getType()) {
            case WORKFLOW_START -> {
                System.out.printf("%s⏱ %s  🚀 WORKFLOW_START [%s]%n", prefix, timeStr, event.getSource());
                printDetails(indent, event.getDetails());
            }
            case WORKFLOW_STEP_START -> {
                System.out.printf("%s⏱ %s  📌 STEP_START [%s]%n", prefix, timeStr, event.getSource());
                printDetails(indent + "│  ", event.getDetails());
            }
            case AGENT_START -> {
                System.out.printf("%s⏱ %s  🤖 AGENT_START [%s]%n", prefix, timeStr, event.getSource());
                printDetails(indent + "│  ", event.getDetails());
            }
            case MODEL_REQUEST -> {
                System.out.printf("%s⏱ %s  📤 MODEL_REQUEST%n", prefix, timeStr);
                printDetails(indent + "│  ", event.getDetails());
            }
            case MODEL_RESPONSE -> {
                Map<String, Object> details = event.getDetails();
                long duration = details.containsKey("durationMs") ? (long) details.get("durationMs") : 0;
                System.out.printf("%s⏱ %s  📥 MODEL_RESPONSE (%dms)%n", prefix, timeStr, duration);
                printDetails(indent + "│  ", details, "durationMs"); // Skip durationMs in details output
            }
            case TOOL_CALL_REQUEST -> {
                System.out.printf("%s⏱ %s  🛠️ TOOL_CALL_REQUEST [%s]%n", prefix, timeStr, event.getSource());
                printDetails(indent + "│  ", event.getDetails());
            }
            case TOOL_CALL_RESULT -> {
                Map<String, Object> details = event.getDetails();
                long duration = details.containsKey("durationMs") ? (long) details.get("durationMs") : 0;
                System.out.printf("%s⏱ %s  🔧 TOOL_CALL_RESULT [%s] (%dms)%n", prefix, timeStr, event.getSource(),
                        duration);
                printDetails(indent + "│  ", details, "durationMs");
            }
            case AGENT_END -> {
                System.out.printf("%s⏱ %s  ✅ AGENT_END [%s]%n", prefix, timeStr, event.getSource());
                printDetails(indent + "│  ", event.getDetails());
            }
            case WORKFLOW_STEP_END -> {
                Map<String, Object> details = event.getDetails();
                long duration = details.containsKey("durationMs") ? (long) (details.get("durationMs")) : 0;
                System.out.printf("%s⏱ %s  🏁 STEP_END [%s] (%dms)%n", prefix, timeStr, event.getSource(), duration);
                printDetails(indent + "   ", details, "durationMs");
            }
            case WORKFLOW_END -> {
                System.out.printf("%s⏱ %s  🏁 WORKFLOW_END [%s]%n", prefix, timeStr, event.getSource());
                printDetails(indent + "   ", event.getDetails());
            }
            default -> {
                System.out.printf("%s⏱ %s  %s [%s]%n", prefix, timeStr, event.getType(), event.getSource());
                printDetails(indent + "   ", event.getDetails());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void printDetails(String indent, Map<String, Object> details, String... skipKeys) {
        if (details == null || details.isEmpty()) {
            return;
        }

        List<String> skipList = List.of(skipKeys);
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (skipList.contains(entry.getKey())) {
                continue;
            }

            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof List<?> list && !list.isEmpty()) {
                System.out.printf("%s%s:%n", indent, key);
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        System.out.printf("%s  - [%s]%n", indent, itemMap.get("type"));
                        String text = (String) itemMap.get("text");
                        if (text != null && !text.isBlank()) {
                            printMultiLine(indent + "    ", text);
                        }
                    } else {
                        System.out.printf("%s  - %s%n", indent, item);
                    }
                }
            } else if (value instanceof String str && str.contains("\\n")) {
                System.out.printf("%s%s:%n", indent, key);
                printMultiLine(indent + "  ", str);
            } else if (value instanceof String str && str.length() > 80) {
                System.out.printf("%s%s:%n", indent, key);
                printMultiLine(indent + "  ", str);
            } else {
                System.out.printf("%s%s: %s%n", indent, key, value);
            }
        }
        System.out.println(indent); // Empty line for vertical spacing
    }

    private static void printMultiLine(String indent, String text) {
        String[] lines = text.replace("\\n", "\n").split("\n");
        for (String line : lines) {
            System.out.printf("%s%s%n", indent, line);
        }
    }

    private static boolean isLastSibling(List<DebugEvent> events, int currentIndex) {
        int currentDepth = events.get(currentIndex).getDepth();
        for (int i = currentIndex + 1; i < events.size(); i++) {
            int nextDepth = events.get(i).getDepth();
            if (nextDepth < currentDepth) {
                return true; // Node at shallower depth encountered, this is the last sibling
            }
            if (nextDepth == currentDepth) {
                return false; // Found another sibling
            }
            // nextDepth > currentDepth means it's a child, keep looking
        }
        return true; // No more events, must be the last
    }
}
