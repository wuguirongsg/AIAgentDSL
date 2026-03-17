package com.agentdsl.tools;

import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.tools.builtin.FileTool;
import com.agentdsl.tools.builtin.HttpTool;
import com.agentdsl.tools.builtin.JsonTool;
import com.agentdsl.tools.builtin.ExcelTool;
import com.agentdsl.tools.builtin.PdfTool;
import com.agentdsl.tools.builtin.ImageTool;
import com.agentdsl.tools.builtin.CmdTool;
import com.agentdsl.tools.builtin.DatabaseTool;
import com.agentdsl.tools.builtin.WebSearchTool;
import com.agentdsl.tools.builtin.CodeExecutionTool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内置工具注册表。
 * 提供预定义的工具集合，包括 HTTP、JSON、File、CodeExecution 等工具。
 *
 * <p>代码执行安全策略可通过以下方式控制（优先级由高到低）：
 * <ol>
 *   <li>{@link #getBuiltinTools(List, boolean)} 的 {@code codeSecurityEnabled} 参数</li>
 *   <li>系统属性 {@code -Dagentdsl.code.security.disabled=true}</li>
 *   <li>环境变量 {@code AGENTDSL_CODE_SECURITY_DISABLED=true}</li>
 * </ol>
 */
public class BuiltinToolRegistry {

    private static volatile List<ToolSpec> cachedTools;

    /**
     * 获取所有内置工具（无文件白名单限制）。
     * 结果会被缓存，多次调用返回同一列表。
     * CodeExecutionTool 的安全策略由环境变量 {@code AGENTDSL_CODE_SECURITY_DISABLED} 控制。
     */
    public static List<ToolSpec> getBuiltinTools() {
        if (cachedTools == null) {
            synchronized (BuiltinToolRegistry.class) {
                if (cachedTools == null) {
                    List<ToolSpec> tools = new ArrayList<>();
                    tools.addAll(ToolScanner.scan(new HttpTool()));
                    tools.addAll(ToolScanner.scan(new JsonTool()));
                    tools.addAll(ToolScanner.scan(new FileTool()));
                    tools.addAll(ToolScanner.scan(new ExcelTool()));
                    tools.addAll(ToolScanner.scan(new PdfTool()));
                    tools.addAll(ToolScanner.scan(new ImageTool()));
                    tools.addAll(ToolScanner.scan(new CmdTool()));
                    tools.addAll(ToolScanner.scan(new DatabaseTool()));
                    tools.addAll(ToolScanner.scan(new WebSearchTool()));
                    // 安全策略读取环境变量（默认启用）
                    tools.addAll(ToolScanner.scan(new CodeExecutionTool()));
                    cachedTools = Collections.unmodifiableList(tools);
                }
            }
        }
        return cachedTools;
    }

    /**
     * 获取所有内置工具（使用自定义 FileTool 白名单）。
     * CodeExecutionTool 的安全策略由环境变量 {@code AGENTDSL_CODE_SECURITY_DISABLED} 控制。
     */
    public static List<ToolSpec> getBuiltinTools(List<String> fileAllowedDirectories) {
        return getBuiltinTools(fileAllowedDirectories, resolveCodeSecurityEnabled());
    }

    /**
     * 获取所有内置工具（完整参数版本）。
     *
     * @param fileAllowedDirectories FileTool 允许访问的目录白名单
     * @param codeSecurityEnabled    {@code true} 启用代码执行安全黑名单（推荐生产），
     *                               {@code false} 关闭安全检查（允许 urllib、subprocess 等）
     */
    public static List<ToolSpec> getBuiltinTools(List<String> fileAllowedDirectories, boolean codeSecurityEnabled) {
        List<ToolSpec> tools = new ArrayList<>();
        tools.addAll(ToolScanner.scan(new HttpTool()));
        tools.addAll(ToolScanner.scan(new JsonTool()));
        tools.addAll(ToolScanner.scan(new FileTool(fileAllowedDirectories)));
        tools.addAll(ToolScanner.scan(new ExcelTool()));
        tools.addAll(ToolScanner.scan(new PdfTool()));
        tools.addAll(ToolScanner.scan(new ImageTool()));
        tools.addAll(ToolScanner.scan(new CmdTool()));
        tools.addAll(ToolScanner.scan(new DatabaseTool()));
        tools.addAll(ToolScanner.scan(new WebSearchTool()));
        tools.addAll(ToolScanner.scan(new CodeExecutionTool(codeSecurityEnabled)));
        return Collections.unmodifiableList(tools);
    }

    /**
     * 从系统属性或环境变量读取代码执行安全策略，默认启用。
     */
    private static boolean resolveCodeSecurityEnabled() {
        String sysProp = System.getProperty("agentdsl.code.security.disabled", "false");
        String envVar = System.getenv("AGENTDSL_CODE_SECURITY_DISABLED");
        boolean disabled = "true".equalsIgnoreCase(sysProp) || "true".equalsIgnoreCase(envVar);
        return !disabled;
    }
}
