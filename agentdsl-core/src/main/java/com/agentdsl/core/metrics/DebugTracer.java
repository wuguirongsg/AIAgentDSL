package com.agentdsl.core.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 调试追踪全局服务。
 * 使用 ThreadLocal 收集当前线程的运行事件，实现零侵入、几乎零开销的监控。
 */
public class DebugTracer {

    private static final ThreadLocal<Boolean> ENABLED = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<List<DebugEvent>> EVENTS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Integer> CURRENT_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * 开启当前线程的调试追踪。
     */
    public static void enable() {
        ENABLED.set(true);
        EVENTS.get().clear();
        CURRENT_DEPTH.set(0);
    }

    /**
     * 关闭当前线程的调试追踪并清理数据。
     */
    public static void disable() {
        ENABLED.set(false);
        EVENTS.get().clear();
        CURRENT_DEPTH.set(0);
    }

    /**
     * 检查当前线程是否已开启调试追踪。
     * 所有埋点处都应先调用此方法，以确保非 debug 模式下零开销。
     */
    public static boolean isEnabled() {
        return ENABLED.get();
    }

    /**
     * 增加当前嵌套深度（例如进入子工作流、Agent 内部循环时）。
     */
    public static void enter() {
        if (!isEnabled())
            return;
        CURRENT_DEPTH.set(CURRENT_DEPTH.get() + 1);
    }

    /**
     * 减少当前嵌套深度。
     */
    public static void exit() {
        if (!isEnabled())
            return;
        int depth = CURRENT_DEPTH.get();
        if (depth > 0) {
            CURRENT_DEPTH.set(depth - 1);
        }
    }

    /**
     * 记录一个具有指定嵌套深度的事件。
     */
    public static void record(DebugEvent.Type type, String source, java.util.Map<String, Object> details,
            int customDepth) {
        if (!isEnabled())
            return;
        EVENTS.get().add(new DebugEvent(type, source, details, customDepth));
    }

    /**
     * 记录一个事件，使用当前默认深度。
     */
    public static void record(DebugEvent.Type type, String source, java.util.Map<String, Object> details) {
        if (!isEnabled())
            return;
        EVENTS.get().add(new DebugEvent(type, source, details, CURRENT_DEPTH.get()));
    }

    /**
     * 获取当前线程收集到的所有追踪事件。
     * 
     * @return 不可变的事件列表快照
     */
    public static List<DebugEvent> getEvents() {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(EVENTS.get()));
    }

    /**
     * 清空当前线程的追踪事件，但不关闭追踪器（下一次执行可以继续收集）。
     */
    public static void clear() {
        EVENTS.get().clear();
        CURRENT_DEPTH.set(0);
    }
}
