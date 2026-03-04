package com.agentdsl.core.dsl

import com.agentdsl.core.spec.ParameterSpec
import com.agentdsl.core.spec.SkillSpec

/**
 * Skill 块的委托类。
 * 处理 skill("name") { ... } 内部的所有关键字。
 *
 * 支持两类技能：
 * - Prompt Skill：type "prompt"; description "..."; instruction "..."
 * - Logic Skill：type "logic"; description "..."; execute { params -> ... }
 */
class SkillDelegate {

    private final SkillSpec spec

    SkillDelegate(SkillSpec spec) {
        this.spec = spec
    }

    /** type "prompt" 或 type "logic" */
    void type(String skillType) {
        spec.setTypeFromString(skillType)
    }

    /** description "技能的语义描述（供 LLM 识别何时调用）" */
    void description(String desc) {
        spec.description = desc
    }

    /**
     * instruction "指令模板"（Prompt Skill 专用）。
     * 调用此 Skill 时，指令会注入到 LLM System Prompt 中。
     */
    void instruction(String inst) {
        spec.instruction = inst
    }

    /**
     * execute { params -> ... }（Logic Skill 专用）。
     * params 是 Map<String, Object>，返回值为执行结果字符串。
     */
    void execute(Closure body) {
        spec.executeBody = body
    }

    /** parameter { name "..."; type "..."; description "..."; required true } */
    void parameter(@DelegatesTo(ParameterDelegate) Closure config) {
        def paramSpec = new ParameterSpec()
        def delegate = new ParameterDelegate(paramSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.parameters << paramSpec
    }

    /**
     * requires "tool-name"（声明 Logic Skill 依赖的工具）。
     * 可多次调用，每次添加一个工具引用。
     */
    void requires(String toolRef) {
        spec.requiredToolRefs << toolRef
    }

    /**
     * requires "tool1", "tool2", ...（批量声明工具依赖）。
     */
    void requires(String... toolRefs) {
        spec.requiredToolRefs.addAll(toolRefs as List)
    }

}
