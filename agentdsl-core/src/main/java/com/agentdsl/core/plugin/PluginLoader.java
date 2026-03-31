package com.agentdsl.core.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 插件加载器：通过 Java SPI 自动发现并加载所有 {@link AgentDslPlugin}。
 * <p>
 * 按 {@link AgentDslPlugin#priority()} 降序排列，优先级高的先注册。
 * core 启动时调用 {@link #loadAll()} 一次即可，无需手动注册。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * DefaultPluginRegistry registry = new DefaultPluginRegistry();
 * List<AgentDslPlugin> plugins = PluginLoader.loadAll();
 * plugins.forEach(p -> p.register(registry));
 * }</pre>
 */
public final class PluginLoader {

    private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

    private PluginLoader() {
    }

    /**
     * 通过 ServiceLoader 加载所有 AgentDslPlugin 实现，
     * 按 priority 降序排列。
     *
     * @return 已排序的插件列表（不可变）
     */
    public static List<AgentDslPlugin> loadAll() {
        return loadAll(Thread.currentThread().getContextClassLoader());
    }

    /**
     * 通过指定 ClassLoader 加载插件（测试场景可能需要）。
     *
     * @param classLoader 类加载器
     * @return 已排序的插件列表（不可变）
     */
    public static List<AgentDslPlugin> loadAll(ClassLoader classLoader) {
        List<AgentDslPlugin> plugins = new ArrayList<>();
        ServiceLoader<AgentDslPlugin> loader = ServiceLoader.load(AgentDslPlugin.class, classLoader);

        for (AgentDslPlugin plugin : loader) {
            plugins.add(plugin);
            log.info("发现 AgentDSL 插件: id={}, priority={}, keywords={}",
                    plugin.pluginId(), plugin.priority(), plugin.contributedKeywords());
        }

        // 按优先级降序排列
        plugins.sort(Comparator.comparingInt(AgentDslPlugin::priority).reversed());

        log.info("共加载 {} 个 AgentDSL 插件", plugins.size());
        return Collections.unmodifiableList(plugins);
    }

    /**
     * 加载插件并注册到指定 registry。
     * <p>
     * 便捷方法，等同于 {@code loadAll().forEach(p -> p.register(registry))}。
     *
     * @param registry 目标注册表
     * @return 加载的插件列表
     */
    public static List<AgentDslPlugin> loadAndRegister(DefaultPluginRegistry registry) {
        List<AgentDslPlugin> plugins = loadAll();
        for (AgentDslPlugin plugin : plugins) {
            try {
                plugin.register(registry);
                log.info("插件注册成功: {}", plugin.pluginId());
            } catch (Exception e) {
                log.error("插件注册失败: {}", plugin.pluginId(), e);
            }
        }
        return plugins;
    }
}
