package com.agentdsl.core.plugin;

/**
 * 插件注册表接口。
 * <p>
 * 定义插件可以注册的扩展点类型。插件在 {@link AgentDslPlugin#register(PluginRegistry)}
 * 中调用这些方法完成自我注册，core 模块通过注册表查找扩展实现。
 *
 * @see DefaultPluginRegistry
 */
public interface PluginRegistry {

    /**
     * 注册一个 ChatMemory 工厂。
     * <p>
     * 当 DSL 中 {@code memory { type "xxx" }} 时，根据 type 找对应工厂创建实例。
     *
     * @param memoryType 记忆类型标识，如 "hypergraph"
     * @param factory    工厂实现
     */
    void registerMemoryFactory(String memoryType, MemoryFactory factory);

    /**
     * 注册一个内置 Skill。
     * <p>
     * 用于扩展 DSL 中 {@code skills { include "xxx" }} 可用的内置技能名称。
     *
     * @param skillName 技能名称，如 "deep_recall"
     */
    void registerBuiltinSkill(String skillName);

    /**
     * 注册一个内置 Tool 名称。
     *
     * @param toolName 工具名称
     */
    void registerBuiltinTool(String toolName);
}
