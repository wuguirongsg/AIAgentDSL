package com.agentdsl.core.metrics;

import java.util.Collections;
import java.util.Map;

/**
 * 调试追踪事件模型。
 * 记录 AgentDSL 执行过程中的关键节点信息（如 Agent 开始、模型请求、工具调用等）。
 */
public class DebugEvent {

    public enum Type {
        AGENT_START, // Agent 开始处理
        AGENT_END, // Agent 处理完成
        MODEL_REQUEST, // 发送请求给模型
        MODEL_RESPONSE, // 模型返回响应
        TOOL_CALL_REQUEST, // 模型请求调用工具
        TOOL_CALL_RESULT, // 工具调用返回结果
        WORKFLOW_START, // 工作流开始
        WORKFLOW_END, // 工作流结束
        WORKFLOW_STEP_START, // 工作流步骤开始
        WORKFLOW_STEP_END, // 工作流步骤结束
        CODE_EXECUTE, // 纯代码执行（execute 闭包）
        DIRECT_TOOL_CALL, // 直接工具调用（绕过 LLM）
        DIRECT_SKILL_CALL, // 直接 Skill 调用（绕过 LLM）
        DIRECT_MCP_CALL // 直接 MCP 工具调用（绕过 LLM）
    }

    private final Type type;
    private final long timestamp;
    private final String source; // 事件来源，如 agent name 或 workflow name
    private final Map<String, Object> details; // 事件详细数据
    private final int depth; // 事件嵌套深度（用于格式化输出缩进）

    public DebugEvent(Type type, String source, Map<String, Object> details, int depth) {
        this.type = type;
        this.source = source != null ? source : "unknown";
        this.details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        this.depth = depth;
        this.timestamp = System.currentTimeMillis();
    }

    public Type getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s %s - %s",
                timestamp,
                "  ".repeat(Math.max(0, depth)) + type,
                source,
                details);
    }
}
