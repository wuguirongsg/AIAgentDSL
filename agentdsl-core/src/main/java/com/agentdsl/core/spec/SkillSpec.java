package com.agentdsl.core.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能定义规范。
 * 对应 DSL 中的 skill("name") { ... } 块。
 *
 * <p>
 * 技能分为两类：
 * <ul>
 * <li><b>PROMPT</b>（描述型）：通过 instruction 驱动 LLM 完成任务，无需编写逻辑代码。</li>
 * <li><b>LOGIC</b>（逻辑型）：通过 Groovy Closure 实现复杂的多步骤业务逻辑。</li>
 * </ul>
 *
 * <p>
 * 设计原则：Skill 本质是增强型 Tool。运行时会将 SkillSpec 展平为 ToolSpec，
 * 通过现有的 LangChain4j 工具注册、发现、执行通道运行。
 */
public class SkillSpec {

    /**
     * 技能类型枚举。
     */
    public enum SkillType {
        /** 描述型技能：通过 instruction 注入到 LLM 系统提示中驱动任务。 */
        PROMPT,
        /** 逻辑型技能：通过 Groovy Closure 执行业务逻辑。 */
        LOGIC
    }

    // --- 基础字段（两种类型共有）---

    /** 技能唯一标识名 */
    private String name;

    /** 技能描述（语义描述，供 LLM 识别何时调用此技能） */
    private String description;

    /** 技能类型 */
    private SkillType type;

    /** 输入参数定义列表（复用 ParameterSpec） */
    private List<ParameterSpec> parameters = new ArrayList<>();

    /** 依赖的工具引用（Logic 型可引用已注册的工具） */
    private List<String> requiredToolRefs = new ArrayList<>();

    // --- Prompt Skill 专有字段 ---

    /**
     * 指令模板（Prompt 型专有）。
     * 当 Agent 调用此 Skill 时，该指令会注入到 LLM 的 System Prompt 中。
     */
    private String instruction;

    // --- Logic Skill 专有字段 ---

    /**
     * 执行逻辑体（Logic 型专有）。
     * Groovy Closure，接收 params Map，返回 String 结果。
     */
    private Object executeBody; // Groovy Closure

    // --- 构造函数 ---

    public SkillSpec() {
    }

    public SkillSpec(String name) {
        this.name = name;
    }

    // --- Getters & Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SkillType getType() {
        return type;
    }

    public void setType(SkillType type) {
        this.type = type;
    }

    /**
     * 从字符串设置技能类型（DSL 方便使用）。
     * 接受 "prompt" 或 "logic"（不区分大小写）。
     */
    public void setTypeFromString(String typeStr) {
        if (typeStr == null) {
            this.type = null;
            return;
        }
        switch (typeStr.trim().toLowerCase()) {
            case "prompt" -> this.type = SkillType.PROMPT;
            case "logic" -> this.type = SkillType.LOGIC;
            default -> throw new com.agentdsl.core.exception.DslCompilationException(
                    "ADSL-001",
                    "Skill type 无效: '" + typeStr + "'，合法值为: prompt, logic");
        }
    }

    public List<ParameterSpec> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterSpec> parameters) {
        this.parameters = parameters;
    }

    public List<String> getRequiredToolRefs() {
        return requiredToolRefs;
    }

    public void setRequiredToolRefs(List<String> requiredToolRefs) {
        this.requiredToolRefs = requiredToolRefs;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public Object getExecuteBody() {
        return executeBody;
    }

    public void setExecuteBody(Object executeBody) {
        this.executeBody = executeBody;
    }

    // --- 便捷判断方法 ---

    public boolean isPromptSkill() {
        return SkillType.PROMPT == type;
    }

    public boolean isLogicSkill() {
        return SkillType.LOGIC == type;
    }

    @Override
    public String toString() {
        return "SkillSpec{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", description='" + (description != null
                        ? description.substring(0, Math.min(40, description.length())) + "..."
                        : "null")
                + '\'' +
                ", parameters=" + parameters.size() +
                '}';
    }
}
