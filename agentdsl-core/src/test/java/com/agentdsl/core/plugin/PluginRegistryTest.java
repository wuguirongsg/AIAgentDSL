package com.agentdsl.core.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryTest {

    @Test
    @DisplayName("DefaultPluginRegistry 应正确注册和查找 MemoryFactory")
    void shouldRegisterAndFindMemoryFactory() {
        DefaultPluginRegistry registry = new DefaultPluginRegistry();

        MemoryFactory factory = config -> "mock-memory";
        registry.registerMemoryFactory("test-type", factory);

        assertNotNull(registry.getMemoryFactory("test-type"));
        assertEquals("mock-memory", registry.getMemoryFactory("test-type").create(Map.of()));
        assertNull(registry.getMemoryFactory("non-existent"));
        assertTrue(registry.getRegisteredMemoryTypes().contains("test-type"));
    }

    @Test
    @DisplayName("DefaultPluginRegistry 应正确注册 Skill 和 Tool")
    void shouldRegisterSkillsAndTools() {
        DefaultPluginRegistry registry = new DefaultPluginRegistry();

        registry.registerBuiltinSkill("my_skill");
        registry.registerBuiltinTool("my_tool");

        assertTrue(registry.isBuiltinSkill("my_skill"));
        assertFalse(registry.isBuiltinSkill("unknown_skill"));
        assertTrue(registry.isBuiltinTool("my_tool"));
        assertFalse(registry.isBuiltinTool("unknown_tool"));
    }

    @Test
    @DisplayName("PluginLoader.loadAndRegister 应加载 SPI 插件并注册到 registry")
    void shouldLoadPluginsViaSpi() {
        // 此测试依赖 classpath 上有声明了 SPI 的插件 jar
        // 在本模块单独测试时，classpath 上没有 agentdsl-memory-hypergraph，
        // 因此 ServiceLoader 不会发现任何插件，这也是正确行为
        DefaultPluginRegistry registry = new DefaultPluginRegistry();
        List<AgentDslPlugin> plugins = PluginLoader.loadAndRegister(registry);

        // 不强断言数量，只验证接口不抛异常
        assertNotNull(plugins);
    }

    @Test
    @DisplayName("多个插件注册同一 type 时，后注册的应覆盖前者")
    void shouldOverrideOnDuplicateMemoryType() {
        DefaultPluginRegistry registry = new DefaultPluginRegistry();

        registry.registerMemoryFactory("overlap", config -> "first");
        registry.registerMemoryFactory("overlap", config -> "second");

        assertEquals("second", registry.getMemoryFactory("overlap").create(Map.of()));
    }

    @Test
    @DisplayName("AgentDslPlugin 接口默认实现应可用")
    void shouldHaveWorkingDefaults() {
        AgentDslPlugin plugin = new AgentDslPlugin() {
            @Override
            public String pluginId() { return "test"; }

            @Override
            public void register(PluginRegistry registry) {}
        };

        assertEquals("test", plugin.pluginId());
        assertEquals(List.of(), plugin.contributedKeywords());
        assertEquals(0, plugin.priority());
    }
}
