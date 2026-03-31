package com.agentdsl.core.plugin;

import java.util.Map;

/**
 * ChatMemory 工厂接口。
 * <p>
 * 插件注册到 {@link PluginRegistry} 后，运行时根据 memory type
 * 查找对应工厂创建 ChatMemory 实例。
 * <p>
 * 返回 Object 以避免 core 对 LangChain4j 的编译期依赖，
 * 调用方需自行转换为 {@code dev.langchain4j.memory.ChatMemory}。
 */
@FunctionalInterface
public interface MemoryFactory {

    /**
     * 创建 ChatMemory 实例。
     *
     * @param config 来自 DSL MemorySpec 的配置映射
     * @return ChatMemory 实例（Object 类型，调用方做强转）
     */
    Object create(Map<String, Object> config);
}
