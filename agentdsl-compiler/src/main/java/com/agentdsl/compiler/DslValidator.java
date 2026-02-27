package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * DSL 语义校验器。
 * 在编译完成后校验必填项和语义规则。
 */
public class DslValidator {

    /** 合法的 returnType 值 */
    private static final Set<String> VALID_RETURN_TYPES = Set.of(
            "string", "json", "object", "number", "boolean", "array");

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
     * 增强校验：returnType、timeoutSeconds、参数约束
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

        // v1.1+ 增强校验
        validateToolReturnType(tool);
        validateToolTimeout(tool);
        validateToolParameters(tool);
    }

    /**
     * 校验 returnType 是否在合法值列表内。
     */
    private static void validateToolReturnType(ToolSpec tool) {
        if (tool.getReturnType() != null && !VALID_RETURN_TYPES.contains(tool.getReturnType())) {
            throw new DslCompilationException("ADSL-002",
                    "Tool '" + tool.getName() + "' 的 returnType '" + tool.getReturnType()
                            + "' 不合法，允许值: " + VALID_RETURN_TYPES);
        }
    }

    /**
     * 校验 timeoutSeconds 在合理范围内 (1-300)。
     */
    private static void validateToolTimeout(ToolSpec tool) {
        if (tool.getTimeoutSeconds() < 1 || tool.getTimeoutSeconds() > 300) {
            throw new DslCompilationException("ADSL-002",
                    "Tool '" + tool.getName() + "' 的 timeoutSeconds 必须在 1-300 之间，当前值: "
                            + tool.getTimeoutSeconds());
        }
    }

    /**
     * 校验 Tool 的所有参数约束。
     */
    private static void validateToolParameters(ToolSpec tool) {
        for (ParameterSpec param : tool.getParameters()) {
            validateParameter(tool.getName(), param);
        }
    }

    /**
     * 校验单个参数的约束条件。
     */
    private static void validateParameter(String toolName, ParameterSpec param) {
        // pattern 必须是合法的正则表达式
        if (param.getPattern() != null) {
            try {
                Pattern.compile(param.getPattern());
            } catch (PatternSyntaxException e) {
                throw new DslCompilationException("ADSL-002",
                        "Tool '" + toolName + "' 的参数 '" + param.getName()
                                + "' 的 pattern 不是合法的正则表达式: " + e.getMessage());
            }
        }

        // min 必须 <= max
        if (param.getMin() != null && param.getMax() != null && param.getMin() > param.getMax()) {
            throw new DslCompilationException("ADSL-002",
                    "Tool '" + toolName + "' 的参数 '" + param.getName()
                            + "' 的 min(" + param.getMin() + ") 不能大于 max(" + param.getMax() + ")");
        }

        // enumValues 不能为空字符串
        if (param.getEnumValues() != null && param.getEnumValues().isBlank()) {
            throw new DslCompilationException("ADSL-002",
                    "Tool '" + toolName + "' 的参数 '" + param.getName()
                            + "' 的 enumValues 不能为空字符串");
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
