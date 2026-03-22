package com.agentdsl.runtime.skill;

import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.langchain4j.LangChainToolBridge.ToolEntry;
import dev.langchain4j.memory.ChatMemory;

/**
 * 内置 Skill 解析器。
 * 将 DSL 中的 builtin skill 名称解析为运行时可执行工具。
 */
public interface BuiltinSkillResolver {

    boolean supports(String skillName, AgentSpec agentSpec, ChatMemory memory);

    ToolEntry resolve(String skillName, AgentSpec agentSpec, ChatMemory memory);
}
