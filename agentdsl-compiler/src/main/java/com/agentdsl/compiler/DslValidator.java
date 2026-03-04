package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.StepSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

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
     * 校验所有编译产出物，包括引用存在性校验。
     */
    public static void validateAll(List<AgentSpec> agents, List<ToolSpec> tools,
            List<WorkflowSpec> workflows, List<SkillSpec> skills) {
        for (AgentSpec agent : agents) {
            validateAgent(agent);
        }
        for (ToolSpec tool : tools) {
            validateTool(tool);
        }
        for (WorkflowSpec workflow : workflows) {
            validateWorkflow(workflow);
        }
        for (SkillSpec skill : skills) {
            validateSkill(skill);
        }
        // 引用存在性校验
        if (!tools.isEmpty()) {
            validateToolReferences(agents, tools);
        }
        if (!agents.isEmpty() && !workflows.isEmpty()) {
            validateWorkflowAgentReferences(workflows, agents);
        }
        if (!skills.isEmpty()) {
            validateSkillReferences(agents, skills);
        }
    }

    /**
     * 向后兼容重载方法（无 Skills 参数版本）。
     */
    public static void validateAll(List<AgentSpec> agents, List<ToolSpec> tools,
            List<WorkflowSpec> workflows) {
        validateAll(agents, tools, workflows, List.of());
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

    // -----------------------------------------------------------------------
    // Skill 校验
    // -----------------------------------------------------------------------

    /**
     * 校验单个 Skill 定义。
     * 必填项：name, description, type。
     * Prompt 型：必须有 instruction。
     * Logic 型：必须有 execute。
     */
    public static void validateSkill(SkillSpec skill) {
        if (skill.getName() == null || skill.getName().isBlank()) {
            throw new DslCompilationException("ADSL-001",
                    "Skill 缺少必填项: name");
        }
        if (skill.getDescription() == null || skill.getDescription().isBlank()) {
            throw new DslCompilationException("ADSL-001",
                    "Skill '" + skill.getName() + "' 缺少必填项: description");
        }
        if (skill.getType() == null) {
            throw new DslCompilationException("ADSL-001",
                    "Skill '" + skill.getName() + "' 缺少必填项: type（prompt 或 logic）");
        }
        switch (skill.getType()) {
            case PROMPT -> {
                if (skill.getInstruction() == null || skill.getInstruction().isBlank()) {
                    throw new DslCompilationException("ADSL-001",
                            "Prompt Skill '" + skill.getName() + "' 缺少必填项: instruction");
                }
            }
            case LOGIC -> {
                if (skill.getExecuteBody() == null) {
                    throw new DslCompilationException("ADSL-001",
                            "Logic Skill '" + skill.getName() + "' 缺少必填项: execute { }");
                }
            }
        }
    }

    /**
     * 校验 Agent 通过 skills { include } 引用的技能是否在已定义的 Skill 列表中存在。
     */
    public static void validateSkillReferences(List<AgentSpec> agents, List<SkillSpec> skills) {
        Set<String> definedSkillNames = skills.stream()
                .map(SkillSpec::getName)
                .collect(Collectors.toSet());

        for (AgentSpec agent : agents) {
            if (agent.getSkillRefs() == null)
                continue;
            for (String ref : agent.getSkillRefs()) {
                if (!definedSkillNames.contains(ref)) {
                    throw new DslCompilationException("ADSL-003",
                            "Agent '" + agent.getName() + "' 引用了未定义的技能: '" + ref
                                    + "'，已定义的技能: " + definedSkillNames);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // 5.6 工具引用存在性校验
    // -----------------------------------------------------------------------

    /**
     * 校验 Agent 通过 {@code include} 引用的工具名称是否在已定义的 Tool 列表中存在。
     *
     * <p>
     * 如果 Agent 引用了未定义的工具，将在编译期抛出 {@link DslCompilationException}，
     * 防止错误配置流入运行截阶段。
     *
     * <p>
     * 注意：内置工具（{@code BuiltinToolRegistry} 提供的 http_get 等）
     * 在运行时才注册，编译期不在 {@code tools} 列表中，因此当前只校验在脚本中
     * 显式定义的工具引用。
     */
    public static void validateToolReferences(List<AgentSpec> agents, List<ToolSpec> tools) {
        Set<String> definedToolNames = tools.stream()
                .map(ToolSpec::getName)
                .collect(Collectors.toSet());

        for (AgentSpec agent : agents) {
            if (agent.getToolRefs() == null)
                continue;
            for (String ref : agent.getToolRefs()) {
                if (!definedToolNames.contains(ref)) {
                    throw new DslCompilationException("ADSL-003",
                            "Agent '" + agent.getName() + "' 引用了未定义的工具: '" + ref
                                    + "'，已定义的工具: " + definedToolNames);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // 5.7 工作流 Agent 引用校验
    // -----------------------------------------------------------------------

    /**
     * 校验 Workflow 中每个 step 引用的 Agent 名称是否在已定义的 Agent 列表中存在。
     *
     * <p>
     * 递归检查显式步骤（Sequential）中的 agentRef。
     * 条件/并行/循环模块内的子步骤同样进行检查。
     */
    public static void validateWorkflowAgentReferences(
            List<WorkflowSpec> workflows, List<AgentSpec> agents) {
        Set<String> definedAgentNames = agents.stream()
                .map(AgentSpec::getName)
                .collect(Collectors.toSet());

        for (WorkflowSpec workflow : workflows) {
            if (workflow.getSteps() == null)
                continue;
            for (StepSpec step : workflow.getSteps()) {
                validateStepAgentRef(workflow.getName(), step, definedAgentNames);
            }
        }
    }

    /**
     * 递归校验单个步骤的 Agent 引用。
     */
    private static void validateStepAgentRef(
            String workflowName, StepSpec step, Set<String> definedAgentNames) {
        // 顺序步骤：检查 agentRef
        if (step.getAgentRef() != null && !step.getAgentRef().isBlank()) {
            if (!definedAgentNames.contains(step.getAgentRef())) {
                throw new DslCompilationException("ADSL-003",
                        "Workflow '" + workflowName + "' 的步骤 '" + step.getName()
                                + "' 引用了未定义的 Agent: '" + step.getAgentRef()
                                + "'，已定义的 Agent: " + definedAgentNames);
            }
        }

        // 条件分支内的子步骤
        if (step.getBranches() != null) {
            step.getBranches().values().forEach(branchSteps -> {
                for (StepSpec subStep : branchSteps) {
                    validateStepAgentRef(workflowName, subStep, definedAgentNames);
                }
            });
        }

        // 并行步骤
        if (step.getParallelSteps() != null) {
            for (StepSpec subStep : step.getParallelSteps()) {
                validateStepAgentRef(workflowName, subStep, definedAgentNames);
            }
        }

        // 循环内的步骤
        if (step.getLoopBody() != null) {
            for (StepSpec subStep : step.getLoopBody()) {
                validateStepAgentRef(workflowName, subStep, definedAgentNames);
            }
        }
    }
}
