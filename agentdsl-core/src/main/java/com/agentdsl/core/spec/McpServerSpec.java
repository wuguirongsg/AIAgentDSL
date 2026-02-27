package com.agentdsl.core.spec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个 MCP Server 配置。
 * 对应 DSL 中 mcp { server("name") { ... } } 的内部块。
 */
public class McpServerSpec {

    private String name;
    private String transport = "stdio"; // "stdio" | "http" | "sse"
    private List<String> command; // STDIO 模式的启动命令
    private String url; // HTTP/SSE 模式的 URL
    private Map<String, String> env = new HashMap<>(); // 环境变量
    private int timeout = 60; // 连接超时（秒）
    private boolean logEvents = false; // 是否记录交互日志

    public McpServerSpec() {
    }

    public McpServerSpec(String name) {
        this.name = name;
    }

    // --- Getters & Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public List<String> getCommand() {
        return command;
    }

    public void setCommand(List<String> command) {
        this.command = command;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public boolean isLogEvents() {
        return logEvents;
    }

    public void setLogEvents(boolean logEvents) {
        this.logEvents = logEvents;
    }

    @Override
    public String toString() {
        return "McpServerSpec{name='" + name + "', transport='" + transport + "'}";
    }
}
