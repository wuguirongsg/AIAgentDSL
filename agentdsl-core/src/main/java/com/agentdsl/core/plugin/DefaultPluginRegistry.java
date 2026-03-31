package com.agentdsl.core.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link PluginRegistry} 的默认实现。
 * <p>
 * 内部维护各类注册表（Map），供 core / runtime 按名称查找插件扩展。
 * 插件在 {@link AgentDslPlugin#register(PluginRegistry)} 中填充。
 */
public class DefaultPluginRegistry implements PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginRegistry.class);

    private final Map<String, MemoryFactory> memoryFactories = new LinkedHashMap<>();
    private final Set<String> builtinSkills = new LinkedHashSet<>();
    private final Set<String> builtinTools = new LinkedHashSet<>();

    @Override
    public void registerMemoryFactory(String memoryType, MemoryFactory factory) {
        if (memoryType == null || memoryType.isBlank()) {
            throw new IllegalArgumentException("memoryType 不能为空");
        }
        if (factory == null) {
            throw new IllegalArgumentException("MemoryFactory 不能为 null");
        }
        MemoryFactory prev = memoryFactories.put(memoryType, factory);
        if (prev != null) {
            log.warn("MemoryFactory 被覆盖: type={}", memoryType);
        }
        log.debug("注册 MemoryFactory: type={}", memoryType);
    }

    @Override
    public void registerBuiltinSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName 不能为空");
        }
        builtinSkills.add(skillName);
        log.debug("注册内置 Skill: {}", skillName);
    }

    @Override
    public void registerBuiltinTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        builtinTools.add(toolName);
        log.debug("注册内置 Tool: {}", toolName);
    }

    // --- 查询方法（供 core / runtime 使用） ---

    /**
     * 按 memory type 查找 MemoryFactory。
     *
     * @param type 记忆类型，如 "hypergraph"
     * @return 工厂实例，未注册时返回 null
     */
    public MemoryFactory getMemoryFactory(String type) {
        return memoryFactories.get(type);
    }

    /**
     * 获取所有已注册的 memory type。
     */
    public Set<String> getRegisteredMemoryTypes() {
        return Collections.unmodifiableSet(memoryFactories.keySet());
    }

    /**
     * 判断是否为已注册的内置 Skill。
     */
    public boolean isBuiltinSkill(String name) {
        return name != null && builtinSkills.contains(name);
    }

    /**
     * 获取所有已注册的内置 Skill 名称。
     */
    public Set<String> getBuiltinSkills() {
        return Collections.unmodifiableSet(builtinSkills);
    }

    /**
     * 判断是否为已注册的内置 Tool。
     */
    public boolean isBuiltinTool(String name) {
        return name != null && builtinTools.contains(name);
    }

    /**
     * 获取所有已注册的内置 Tool 名称。
     */
    public Set<String> getBuiltinTools() {
        return Collections.unmodifiableSet(builtinTools);
    }
}
