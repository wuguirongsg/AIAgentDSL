package com.agentdsl.core.spec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的全局数据源注册表，供 DatabaseTool 在运行时获取具体的连接信息。
 */
public class DataSourceRegistry {
    private static final Map<String, DataSourceSpec> dataSources = new ConcurrentHashMap<>();

    public static void register(DataSourceSpec spec) {
        if (spec != null && spec.getName() != null) {
            dataSources.put(spec.getName(), spec);
        }
    }

    public static DataSourceSpec get(String name) {
        return dataSources.get(name);
    }

    public static void clear() {
        dataSources.clear();
    }
}
