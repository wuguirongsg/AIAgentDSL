package com.agentdsl.compiler;

import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ToolSpec;

import java.util.Collections;
import java.util.List;

/**
 * DSL 编译结果，包含解析出的所有 Agent 和独立 Tool 定义。
 */
public class DslCompileResult {

    private final List<AgentSpec> agents;
    private final List<ToolSpec> tools;

    public DslCompileResult(List<AgentSpec> agents, List<ToolSpec> tools) {
        this.agents = Collections.unmodifiableList(agents);
        this.tools = Collections.unmodifiableList(tools);
    }

    public List<AgentSpec> getAgents() {
        return agents;
    }

    public List<ToolSpec> getTools() {
        return tools;
    }

    /**
     * 获取第一个 Agent（便捷方法，适用于单 Agent 脚本）。
     */
    public AgentSpec getFirstAgent() {
        if (agents.isEmpty()) {
            return null;
        }
        return agents.get(0);
    }

    @Override
    public String toString() {
        return "DslCompileResult{agents=" + agents.size() + ", tools=" + tools.size() + '}';
    }
}
