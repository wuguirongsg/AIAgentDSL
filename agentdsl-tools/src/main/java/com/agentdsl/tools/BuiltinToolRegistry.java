package com.agentdsl.tools;

import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.tools.builtin.FileTool;
import com.agentdsl.tools.builtin.HttpTool;
import com.agentdsl.tools.builtin.JsonTool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内置工具注册表。
 * 提供预定义的工具集合，包括 HTTP、JSON、File 工具。
 */
public class BuiltinToolRegistry {

    private static volatile List<ToolSpec> cachedTools;

    /**
     * 获取所有内置工具。
     * 结果会被缓存，多次调用返回同一列表。
     */
    public static List<ToolSpec> getBuiltinTools() {
        if (cachedTools == null) {
            synchronized (BuiltinToolRegistry.class) {
                if (cachedTools == null) {
                    List<ToolSpec> tools = new ArrayList<>();
                    tools.addAll(ToolScanner.scan(new HttpTool()));
                    tools.addAll(ToolScanner.scan(new JsonTool()));
                    tools.addAll(ToolScanner.scan(new FileTool()));
                    cachedTools = Collections.unmodifiableList(tools);
                }
            }
        }
        return cachedTools;
    }

    /**
     * 获取所有内置工具（使用自定义 FileTool 白名单）。
     */
    public static List<ToolSpec> getBuiltinTools(List<String> fileAllowedDirectories) {
        List<ToolSpec> tools = new ArrayList<>();
        tools.addAll(ToolScanner.scan(new HttpTool()));
        tools.addAll(ToolScanner.scan(new JsonTool()));
        tools.addAll(ToolScanner.scan(new FileTool(fileAllowedDirectories)));
        return Collections.unmodifiableList(tools);
    }
}
