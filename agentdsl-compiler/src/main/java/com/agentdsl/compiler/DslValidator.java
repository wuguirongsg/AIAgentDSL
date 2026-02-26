package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;

import java.util.List;

/**
 * DSL 语义校验器。
 * 在编译完成后校验必填项和语义规则。
 */
public class DslValidator {

    private DslValidator() {
    }

    /**
     * 校验所有编译产出物。
     */
    public static void validateAll(List<AgentSpec> agents, List<ToolSpec> tools,
            List<WorkflowSpec> workflows) {
        for (AgentSpec agent : agents) {
            validateAgent(agent);
        }
        for (ToolSpec tool : tools) {
            validateTool(tool);
        }
        for (WorkflowSpec workflow : workflows) {
            validateWorkflow(workflow);
        }
    }

    /**
     * 校验 Agent 定义。
     * 必填项：name, model.provider, model.modelName
     */
    public static void validateAgent(AgentSpec agent) {
        if (agent.getName() == null || agent.getName().isBlank()) {
            throw new DslCompilationException("ADSL-001",
                    "Agent 缺少必填项: name");
        }
        if (agent.getModel() == null) {
            throw new DslCompilationException("ADSL-001",
                    "Agent '" + agent.getName() + "' 缺少必填项: model { }");
        }
        if (agent.getModel().getProvider() == null || agent.getModel().getProvider().isBlank()) {
            throw new DslCompilationException("ADSL-001",
                    "Agent '" + agent.getName() + "' 的 model 缺少必填项: provider");
        }
        if (agent.getModel().getModelName() == null || agent.getModel().getModelName().isBlank()) {
            throw new DslCompilationException("ADSL-001",
                    "Agent '" + agent.getName() + "' 的 model 缺少必填项: modelName");
        }
    }

    /**
     * 校验 Tool 定义。
     * 必填项：name, description, execute
     */
    public static void validateTool(ToolSpec tool) {
        if (tool.getName() == null || tool.getName().isBlank()) {
            throw new DslCompilationException("ADSL-001",
                    "Tool 缺少必填项: name");
        }
        if (tool.getDescription() == null || tool.getDescription().isBlank()) {
            throw new DslCompilationException("ADSL-001",
                    "Tool '" + tool.getName() + "' 缺少必填项: description");
        }
        if (tool.getExecuteBody() == null) {
            throw new DslCompilationException("ADSL-001",
                    "Tool '" + tool.getName() + "' 缺少必填项: execute { }");
        }
    }

    /**
     * 校验 Workflow 定义。
     * 必填项：name, steps（至少 1 个 step）
     */
    public static void validateWorkflow(WorkflowSpec workflow) {
        if (workflow.getName() == null || workflow.getName().isBlank()) {
            throw new DslCompilationException("ADSL-001",
                    "Workflow 缺少必填项: name");
        }
        if (workflow.getSteps() == null || workflow.getSteps().isEmpty()) {
            throw new DslCompilationException("ADSL-001",
                    "Workflow '" + workflow.getName() + "' 缺少必填项: steps（至少需要 1 个步骤）");
        }
    }
}
