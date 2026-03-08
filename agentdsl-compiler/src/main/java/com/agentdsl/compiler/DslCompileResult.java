package com.agentdsl.compiler;

import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import com.agentdsl.core.spec.DataSourceSpec;

import java.util.Collections;
import java.util.List;

/**
 * DSL 编译结果，包含解析出的所有 Agent、独立 Tool、Workflow 和 Skill 定义。
 */
public class DslCompileResult {

    private final List<AgentSpec> agents;
    private final List<ToolSpec> tools;
    private final List<WorkflowSpec> workflows;
    private final List<SkillSpec> skills;
    private final List<DataSourceSpec> datasources;
    private final List<Diagnostic> diagnostics;

    public DslCompileResult(List<AgentSpec> agents, List<ToolSpec> tools) {
        this(agents, tools, List.of(), List.of());
    }

    public DslCompileResult(List<AgentSpec> agents, List<ToolSpec> tools, List<WorkflowSpec> workflows) {
        this(agents, tools, workflows, List.of());
    }

    public DslCompileResult(List<AgentSpec> agents, List<ToolSpec> tools,
            List<WorkflowSpec> workflows, List<SkillSpec> skills) {
        this(agents, tools, workflows, skills, List.of(), List.of());
    }

    public DslCompileResult(List<AgentSpec> agents, List<ToolSpec> tools,
            List<WorkflowSpec> workflows, List<SkillSpec> skills, List<DataSourceSpec> datasources,
            List<Diagnostic> diagnostics) {
        this.agents = Collections.unmodifiableList(agents);
        this.tools = Collections.unmodifiableList(tools);
        this.workflows = Collections.unmodifiableList(workflows);
        this.skills = Collections.unmodifiableList(skills);
        this.datasources = Collections.unmodifiableList(datasources);
        this.diagnostics = Collections.unmodifiableList(diagnostics);
    }

    public List<AgentSpec> getAgents() {
        return agents;
    }

    public List<ToolSpec> getTools() {
        return tools;
    }

    public List<WorkflowSpec> getWorkflows() {
        return workflows;
    }

    public List<SkillSpec> getSkills() {
        return skills;
    }

    public List<DataSourceSpec> getDatasources() {
        return datasources;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
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

    /**
     * 获取第一个 Workflow（便捷方法，适用于单 Workflow 脚本）。
     */
    public WorkflowSpec getFirstWorkflow() {
        if (workflows.isEmpty()) {
            return null;
        }
        return workflows.get(0);
    }

    @Override
    public String toString() {
        return "DslCompileResult{agents=" + agents.size() +
                ", tools=" + tools.size() +
                ", workflows=" + workflows.size() +
                ", skills=" + skills.size() +
                ", datasources=" + datasources.size() +
                ", diagnostics=" + diagnostics.size() + '}';
    }
}
