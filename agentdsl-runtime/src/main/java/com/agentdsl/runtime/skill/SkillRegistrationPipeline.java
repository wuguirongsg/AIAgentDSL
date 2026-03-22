package com.agentdsl.runtime.skill;

import com.agentdsl.core.builtin.BuiltinSkillRegistry;
import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.langchain4j.LangChainToolBridge;
import com.agentdsl.langchain4j.LangChainToolBridge.ToolEntry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 统一的 Skill 注册流水线。
 * 负责处理全局 Skill、inline Skill 和 builtin Skill，并返回需要追加到 systemPrompt 的内容。
 */
public final class SkillRegistrationPipeline {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistrationPipeline.class);

    private final Map<String, SkillSpec> globalSkills;
    private final List<BuiltinSkillResolver> builtinSkillResolvers;
    private final LangChainToolBridge toolBridge;

    public SkillRegistrationPipeline(Map<String, SkillSpec> globalSkills,
            List<BuiltinSkillResolver> builtinSkillResolvers,
            LangChainToolBridge toolBridge) {
        this.globalSkills = globalSkills;
        this.builtinSkillResolvers = builtinSkillResolvers;
        this.toolBridge = toolBridge;
    }

    public String register(AgentSpec agentSpec,
            ChatMemory memory,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors) {
        StringBuilder promptAppend = new StringBuilder();
        registerSkillRefs(agentSpec, memory, toolSpecifications, toolExecutors, promptAppend);
        registerInlineSkills(agentSpec, toolSpecifications, toolExecutors, promptAppend);
        return promptAppend.toString();
    }

    private void registerSkillRefs(AgentSpec agentSpec,
            ChatMemory memory,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            StringBuilder promptAppend) {
        if (agentSpec.getSkillRefs() == null) {
            return;
        }

        for (String skillRef : agentSpec.getSkillRefs()) {
            SkillSpec skill = globalSkills.get(skillRef);
            if (skill == null) {
                registerBuiltinSkill(skillRef, agentSpec, memory, toolSpecifications, toolExecutors);
                continue;
            }
            registerDeclaredSkill(skill, agentSpec.getName(), "global", toolSpecifications, toolExecutors, promptAppend);
        }
    }

    private void registerInlineSkills(AgentSpec agentSpec,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            StringBuilder promptAppend) {
        if (agentSpec.getInlineSkills() == null) {
            return;
        }

        for (SkillSpec skill : agentSpec.getInlineSkills()) {
            registerDeclaredSkill(skill, agentSpec.getName(), "inline", toolSpecifications, toolExecutors, promptAppend);
        }
    }

    private void registerDeclaredSkill(SkillSpec skill,
            String agentName,
            String source,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            StringBuilder promptAppend) {
        if (skill.isLogicSkill()) {
            ToolSpec skillAsTool = flattenSkillToTool(skill);
            ToolEntry entry = toolBridge.convert(skillAsTool);
            toolSpecifications.add(entry.specification());
            toolExecutors.put(entry.specification().name(), entry.executor());
            log.info("Agent '{}' 注册 {} Logic Skill 为工具: {}", agentName, source, skill.getName());
            return;
        }

        promptAppend.append("\n\n---\n### Skill: ").append(skill.getName())
                .append("\n").append(skill.getInstruction());
        log.info("Agent '{}' 注入 {} Prompt Skill 到 systemPrompt: {}", agentName, source, skill.getName());
    }

    private void registerBuiltinSkill(String skillRef,
            AgentSpec agentSpec,
            ChatMemory memory,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors) {
        BuiltinSkillResolver resolver = findBuiltinSkillResolver(skillRef, agentSpec, memory);
        if (resolver == null) {
            if (BuiltinSkillRegistry.isBuiltinSkill(skillRef)) {
                throw new DslRuntimeException("ADSL-051",
                        "Agent '" + agentSpec.getName() + "' 引用了内置 Skill '" + skillRef
                                + "'，但当前配置不满足该 Skill 的运行条件（如 deep_recall 需要 memory.type = hypergraph）");
            }
            throw new DslRuntimeException("ADSL-011",
                    "Agent '" + agentSpec.getName() + "' 引用了未注册的技能: " + skillRef);
        }
        if (toolExecutors.containsKey(skillRef)) {
            return;
        }

        ToolEntry entry = resolver.resolve(skillRef, agentSpec, memory);
        toolSpecifications.add(entry.specification());
        toolExecutors.put(entry.specification().name(), entry.executor());
        log.info("Agent '{}' 注册内置 Skill: {}", agentSpec.getName(), entry.specification().name());
    }

    private BuiltinSkillResolver findBuiltinSkillResolver(String skillName, AgentSpec agentSpec, ChatMemory memory) {
        for (BuiltinSkillResolver resolver : builtinSkillResolvers) {
            if (resolver.supports(skillName, agentSpec, memory)) {
                return resolver;
            }
        }
        return null;
    }

    private ToolSpec flattenSkillToTool(SkillSpec skill) {
        ToolSpec toolSpec = new ToolSpec(skill.getName());
        toolSpec.setDescription(skill.getDescription());
        toolSpec.setParameters(skill.getParameters());

        if (skill.isLogicSkill()) {
            toolSpec.setExecuteBody(skill.getExecuteBody());
            return toolSpec;
        }

        final String instruction = skill.getInstruction();
        groovy.lang.Closure<?> promptClosure = new groovy.lang.Closure<String>(this) {
            @Override
            public String call(Object... args) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = args.length > 0
                        ? (Map<String, Object>) args[0]
                        : java.util.Collections.emptyMap();
                StringBuilder prompt = new StringBuilder(instruction);
                if (!params.isEmpty()) {
                    prompt.append("\n\n输入参数:\n");
                    params.forEach((k, v) -> prompt.append("- ").append(k).append(": ").append(v).append("\n"));
                }
                return prompt.toString();
            }
        };
        toolSpec.setExecuteBody(promptClosure);
        return toolSpec;
    }
}
