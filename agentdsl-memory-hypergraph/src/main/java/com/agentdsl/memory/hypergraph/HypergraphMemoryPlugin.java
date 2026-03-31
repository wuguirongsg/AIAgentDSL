package com.agentdsl.memory.hypergraph;

import com.agentdsl.core.plugin.AgentDslPlugin;
import com.agentdsl.core.plugin.PluginRegistry;

import java.util.List;

/**
 * 超图记忆系统的 AgentDSL 插件声明。
 * <p>
 * 通过 Java SPI 被 {@link com.agentdsl.core.plugin.PluginLoader} 自动发现，
 * 在 {@link #register(PluginRegistry)} 中注册 "hypergraph" MemoryFactory
 * 和 "deep_recall" 内置 Skill。
 * <p>
 * core 模块无需感知此类的存在，只需 classpath 上有此 jar 即可。
 */
public class HypergraphMemoryPlugin implements AgentDslPlugin {

    @Override
    public String pluginId() {
        return "memory-hypergraph";
    }

    @Override
    public List<String> contributedKeywords() {
        return List.of("stm", "ltm", "decay", "consolidation", "deepRecallThreshold", "vector");
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public void register(PluginRegistry registry) {
        // 1. 注册 memory type "hypergraph" 的工厂
        HypergraphMemoryProvider provider = new HypergraphMemoryProvider();
        registry.registerMemoryFactory("hypergraph", provider::create);

        // 2. 注册内置 Skill
        registry.registerBuiltinSkill("deep_recall");
    }
}
