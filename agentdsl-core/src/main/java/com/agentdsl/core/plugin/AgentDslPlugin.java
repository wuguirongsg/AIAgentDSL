package com.agentdsl.core.plugin;

import java.util.List;

/**
 * AgentDSL 插件统一接口。
 * <p>
 * 所有扩展模块（memory 插件、tool 插件、skill 插件等）实现此接口，
 * 通过 Java SPI ({@code META-INF/services/com.agentdsl.core.plugin.AgentDslPlugin})
 * 被 {@link PluginLoader} 自动发现和加载。
 * <p>
 * core 模块通过此接口与插件交互，永远不需要直接依赖任何插件模块。
 *
 * @see PluginRegistry
 * @see PluginLoader
 */
public interface AgentDslPlugin {

    /**
     * 插件唯一标识。
     *
     * @return 如 "memory-hypergraph"、"tool-browser"
     */
    String pluginId();

    /**
     * 插件提供的 DSL 关键字列表（用于文档和冲突检测）。
     *
     * @return 如 ["stm", "ltm", "decay", "consolidation"]
     */
    default List<String> contributedKeywords() {
        return List.of();
    }

    /**
     * 向 DSL 运行时注册扩展能力。
     * <p>
     * 插件在此方法中把自己的 MemoryFactory、内置 Skill、内置 Tool 等
     * 注册到 {@link PluginRegistry}，core 通过注册表按需查找。
     *
     * @param registry 插件注册表
     */
    void register(PluginRegistry registry);

    /**
     * 插件优先级，数值越大优先级越高。
     * <p>
     * 当多个插件注册同一个 type 时，优先级高的生效。
     *
     * @return 优先级，默认 0
     */
    default int priority() {
        return 0;
    }
}
