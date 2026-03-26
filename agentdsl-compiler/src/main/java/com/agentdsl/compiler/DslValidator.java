package com.agentdsl.compiler;

import com.agentdsl.core.builtin.BuiltinSkillRegistry;
import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.AutonomousSpec;
import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.StepSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;

import java.util.ArrayList;
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

    /** 合法的 autonomous execution_mode 值 */
    private static final Set<String> VALID_EXECUTION_MODES = Set.of("plan", "fast");

    private DslValidator() {
    }

    /**
     * 校验所有编译产出物，包括引用存在性校验。
     *
     * @param knownBuiltinToolNames 运行时已知的内置工具名称，这些工具的引用不产生 Warning
     * @return 编译过程产生的诊断信息列表
     */
    public static List<Diagnostic> validateAll(List<AgentSpec> agents, List<ToolSpec> tools,
            List<WorkflowSpec> workflows, List<SkillSpec> skills, Set<String> knownBuiltinToolNames) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (AgentSpec agent : agents) {
            validateAgent(agent, diagnostics);
        }
        for (ToolSpec tool : tools) {
            validateTool(tool, diagnostics);
        }
        for (WorkflowSpec workflow : workflows) {
            validateWorkflow(workflow);
        }
        for (SkillSpec skill : skills) {
            validateSkill(skill);
        }
        // 引用存在性校验（即使脚本无自定义 tool，也需校验内置工具引用）
        validateToolReferences(agents, tools, diagnostics, knownBuiltinToolNames);
        if (!agents.isEmpty() && !workflows.isEmpty()) {
            validateWorkflowAgentReferences(workflows, agents);
        }
        if (!agents.isEmpty()) {
            validateSkillReferences(agents, skills);
        }

        return diagnostics;
    }

    /**
     * 向后兼容重载方法（无 knownBuiltinToolNames 参数版本）。
     */
    public static List<Diagnostic> validateAll(List<AgentSpec> agents, List<ToolSpec> tools,
            List<WorkflowSpec> workflows, List<SkillSpec> skills) {
        return validateAll(agents, tools, workflows, skills, Set.of());
    }

    /**
     * 向后兼容重载方法（无 Skills 和 knownBuiltinToolNames 参数版本）。
     */
    public static List<Diagnostic> validateAll(List<AgentSpec> agents, List<ToolSpec> tools,
            List<WorkflowSpec> workflows) {
        return validateAll(agents, tools, workflows, List.of(), Set.of());
    }

    public static void validateAgent(AgentSpec agent) {
        validateAgent(agent, new ArrayList<>());
    }

    /**
     * 校验 Agent 定义。
     * 必填项：name, model.provider, model.modelName
     * 可选校验：autonomous 配置
     */
    public static void validateAgent(AgentSpec agent, List<Diagnostic> diagnostics) {
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
        // Autonomous 配置校验
        if (agent.isAutonomous()) {
            validateAutonomous(agent, diagnostics);
        }
    }

    /**
     * 校验 Autonomous 配置。
     * - execution_mode 必须是 "plan" 或 "fast"
     * - max_steps 必须 > 0
     * - 自主 Agent 建议配置 tools 或 skills（软性警告）
     */
    private static void validateAutonomous(AgentSpec agent, List<Diagnostic> diagnostics) {
        AutonomousSpec autonomous = agent.getAutonomous();
        String name = agent.getName();

        if (autonomous.getExecutionMode() == null
                || !VALID_EXECUTION_MODES.contains(autonomous.getExecutionMode().toLowerCase())) {
            throw new DslCompilationException("ADSL-001",
                    "Agent '" + name + "' 的 autonomous.execution_mode 必须是 'plan' 或 'fast'，"
                            + "当前值: '" + autonomous.getExecutionMode() + "'");
        }

        if (autonomous.getMaxSteps() <= 0) {
            throw new DslCompilationException("ADSL-001",
                    "Agent '" + name + "' 的 autonomous.max_steps 必须大于 0，"
                            + "当前值: " + autonomous.getMaxSteps());
        }

        // 软性警告：自主 Agent 没有配置任何工具或技能
        boolean hasTools = (agent.getTools() != null && !agent.getTools().isEmpty())
                || (agent.getToolRefs() != null && !agent.getToolRefs().isEmpty());
        boolean hasSkills = (agent.getSkillRefs() != null && !agent.getSkillRefs().isEmpty())
                || (agent.getInlineSkills() != null && !agent.getInlineSkills().isEmpty());
        if (!hasTools && !hasSkills) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                    "自主 Agent '" + name + "' 未配置任何 tools 或 skills，"
                            + "自主模式下通常需要工具来完成多步骤任务。",
                    "Agent: " + name));
        }
    }

    /**
     * 校验 Tool 定义。
     * 必填项：name, description, execute
     * 增强校验：returnType、timeoutSeconds、参数约束
     */
    public static void validateTool(ToolSpec tool, List<Diagnostic> diagnostics) {
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
        validateToolReturnType(tool, diagnostics);
        validateToolTimeout(tool, diagnostics);
        validateToolParameters(tool);
    }

    /**
     * 兼容旧版本方法
     */
    public static void validateTool(ToolSpec tool) {
        validateTool(tool, new ArrayList<>());
    }

    /**
     * 校验 returnType 是否在合法值列表内。
     */
    private static void validateToolReturnType(ToolSpec tool, List<Diagnostic> diagnostics) {
        if (tool.getReturnType() != null && !VALID_RETURN_TYPES.contains(tool.getReturnType())) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                    "Tool '" + tool.getName() + "' 的 returnType '" + tool.getReturnType()
                            + "' 不在推荐的常见类型中: " + VALID_RETURN_TYPES,
                    "Tool: " + tool.getName()));

            // 这里为了平滑升级，可以仅作为 Warning 而不抛出异常。如果要严格限制：
            // throw new DslCompilationException("ADSL-002",
            // "Tool '" + tool.getName() + "' 的 returnType '" + tool.getReturnType()
            // + "' 不合法，允许值: " + VALID_RETURN_TYPES);
        }
    }

    /**
     * 校验 timeoutSeconds 在合理范围内 (1-300)。
     */
    private static void validateToolTimeout(ToolSpec tool, List<Diagnostic> diagnostics) {
        if (tool.getTimeoutSeconds() < 1 || tool.getTimeoutSeconds() > 300) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                    "Tool '" + tool.getName() + "' 的 timeoutSeconds " + tool.getTimeoutSeconds()
                            + " 超出了建议范围 (1-300)。可能导致模型等待过久或被过早中断。",
                    "Tool: " + tool.getName()));
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
     * 每个 step 的执行模式必须互斥。
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
        for (StepSpec step : workflow.getSteps()) {
            validateStepExecutionMode(workflow.getName(), step);
        }
    }

    /**
     * 递归校验步骤执行模式互斥性。
     * SEQUENTIAL 步骤必须且只能指定一种执行模式：agent / execute / tool / skill / mcp。
     */
    private static void validateStepExecutionMode(String workflowName, StepSpec step) {
        if (step.getType() == StepSpec.StepType.SEQUENTIAL) {
            int modeCount = 0;
            List<String> modes = new ArrayList<>();
            if (step.getAgentRef() != null) { modeCount++; modes.add("agent"); }
            if (step.getExecuteClosure() != null) { modeCount++; modes.add("execute"); }
            if (step.getToolRef() != null) { modeCount++; modes.add("tool"); }
            if (step.getSkillRef() != null) { modeCount++; modes.add("skill"); }
            if (step.getMcpServerRef() != null) { modeCount++; modes.add("mcp"); }

            if (modeCount == 0) {
                throw new DslCompilationException("ADSL-004",
                        "Workflow '" + workflowName + "' 的步骤 '" + step.getName()
                                + "' 未指定执行模式，必须使用 agent/execute/tool/skill/mcp 之一");
            }
            if (modeCount > 1) {
                throw new DslCompilationException("ADSL-004",
                        "Workflow '" + workflowName + "' 的步骤 '" + step.getName()
                                + "' 指定了多个执行模式 " + modes + "，这些模式互斥，只能选择一个");
            }
            if (step.getMcpServerRef() != null && (step.getMcpToolRef() == null || step.getMcpToolRef().isBlank())) {
                throw new DslCompilationException("ADSL-004",
                        "Workflow '" + workflowName + "' 的步骤 '" + step.getName()
                                + "' 使用了 mcp 模式但未指定工具名称");
            }
        }
        // 递归校验子步骤
        if (step.getParallelSteps() != null) {
            for (StepSpec sub : step.getParallelSteps()) {
                validateStepExecutionMode(workflowName, sub);
            }
        }
        if (step.getBranches() != null) {
            step.getBranches().values().forEach(branchSteps -> {
                for (StepSpec sub : branchSteps) {
                    validateStepExecutionMode(workflowName, sub);
                }
            });
        }
        if (step.getLoopBody() != null) {
            for (StepSpec sub : step.getLoopBody()) {
                validateStepExecutionMode(workflowName, sub);
            }
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
                if (BuiltinSkillRegistry.isBuiltinSkill(ref)) {
                    // 内置 skill 的名称合法，但还需校验 agent 配置是否满足该 skill 的生效条件
                    validateBuiltinSkillConditions(ref, agent);
                    continue;
                }
                if (!definedSkillNames.contains(ref)) {
                    throw new DslCompilationException("ADSL-003",
                            "Agent '" + agent.getName() + "' 引用了未定义的技能: '" + ref
                                    + "'，已定义的技能: " + definedSkillNames);
                }
            }
        }
    }

    /**
     * 校验内置 skill 的生效条件。
     * 条件知识放在编译器层，core 层的 BuiltinSkillRegistry 只维护名称白名单。
     */
    private static void validateBuiltinSkillConditions(String skillName, AgentSpec agent) {
        switch (skillName) {
            case BuiltinSkillRegistry.DEEP_RECALL -> {
                boolean hasHypergraphMemory = agent.getMemory() != null
                        && "hypergraph".equalsIgnoreCase(agent.getMemory().getType());
                if (!hasHypergraphMemory) {
                    throw new DslCompilationException("ADSL-003",
                            "Agent '" + agent.getName() + "' 引用了内置 Skill 'deep_recall'，"
                                    + "但 deep_recall 需要 memory.type = \"hypergraph\"，"
                                    + "当前 memory type: "
                                    + (agent.getMemory() != null ? agent.getMemory().getType() : "未配置"));
                }
            }
            default -> {
                // 其他内置 skill 暂无额外条件
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
    public static void validateToolReferences(List<AgentSpec> agents, List<ToolSpec> tools,
            List<Diagnostic> diagnostics, Set<String> knownBuiltinToolNames) {
        Set<String> definedToolNames = tools.stream()
                .map(ToolSpec::getName)
                .collect(Collectors.toSet());

        for (AgentSpec agent : agents) {
            if (agent.getToolRefs() == null)
                continue;
            for (String ref : agent.getToolRefs()) {
                if (!definedToolNames.contains(ref) && !knownBuiltinToolNames.contains(ref)) {
                    // 内置工具或运行时注入的工具可能在编译期无法被发现，因此这里改为产生 Warning 而不是抛出异常
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                            "Agent '" + agent.getName() + "' 引用了脚本当中未定义的工具: '" + ref
                                    + "'，可能是拼写错误，或者该工具是内置/运行期动态注入工具。",
                            "Agent: " + agent.getName()));
                }
            }
        }
    }

    /** 为了向后兼容保留的方法 */
    public static void validateToolReferences(List<AgentSpec> agents, List<ToolSpec> tools,
            List<Diagnostic> diagnostics) {
        validateToolReferences(agents, tools, diagnostics, Set.of());
    }

    /** 为了向后兼容保留的废弃方法 */
    public static void validateToolReferences(List<AgentSpec> agents, List<ToolSpec> tools) {
        validateToolReferences(agents, tools, new ArrayList<>(), Set.of());
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
     * 只有 agent 模式的步骤才需要校验 Agent 引用，直接执行模式的步骤跳过。
     */
    private static void validateStepAgentRef(
            String workflowName, StepSpec step, Set<String> definedAgentNames) {
        // 只有 agent 模式的步骤才校验 agentRef（execute/tool/skill/mcp 步骤跳过）
        if (step.getAgentRef() != null && !step.getAgentRef().isBlank()
                && !step.isDirectExecution()) {
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
