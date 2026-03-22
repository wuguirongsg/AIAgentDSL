package com.agentdsl.runtime.skill;

import com.agentdsl.core.builtin.BuiltinSkillRegistry;
import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.langchain4j.LangChainToolBridge.ToolEntry;
import dev.langchain4j.memory.ChatMemory;

/**
 * 将 memory 插件提供的 capability 适配为 DSL 内置 skill。
 * 对外保留 skills 语义，对内仍复用统一 tool 桥接。
 *
 * 生效条件由本类自行判断：skill 名称合法 + agent 使用了支持该 skill 的 memory 类型。
 */
public final class MemoryBuiltinSkillResolver implements BuiltinSkillResolver {

    private final MemoryCapabilityBridge capabilityBridge;

    public MemoryBuiltinSkillResolver(MemoryCapabilityBridge capabilityBridge) {
        this.capabilityBridge = capabilityBridge;
    }

    @Override
    public boolean supports(String skillName, AgentSpec agentSpec, ChatMemory memory) {
        if (memory == null || !BuiltinSkillRegistry.isBuiltinSkill(skillName)) {
            return false;
        }
        return switch (skillName) {
            case BuiltinSkillRegistry.DEEP_RECALL ->
                agentSpec != null
                        && agentSpec.getMemory() != null
                        && "hypergraph".equalsIgnoreCase(agentSpec.getMemory().getType());
            default -> false;
        };
    }

    @Override
    public ToolEntry resolve(String skillName, AgentSpec agentSpec, ChatMemory memory) {
        Object capability = capabilityBridge.findCapabilityByName(memory, skillName)
                .orElseThrow(() -> new DslRuntimeException("ADSL-051",
                        "Agent '" + agentSpec.getName() + "' 引用了内置 Skill '" + skillName
                                + "'，但当前 memory 未暴露对应 capability"));
        return capabilityBridge.convert(capability, skillName);
    }
}
