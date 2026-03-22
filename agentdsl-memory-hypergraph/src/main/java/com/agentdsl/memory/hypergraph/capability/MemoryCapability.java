package com.agentdsl.memory.hypergraph.capability;

import java.util.List;
import java.util.Map;

/**
 * Memory 插件能力描述。
 * 由宿主通过反射读取，无需依赖 AgentDSL 内部工具类型。
 */
public interface MemoryCapability {

    String getName();

    String getDescription();

    List<Map<String, Object>> getParameters();

    Object execute(Map<String, Object> args);
}
