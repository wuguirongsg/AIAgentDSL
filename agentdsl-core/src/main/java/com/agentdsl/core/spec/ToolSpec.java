package com.agentdsl.core.spec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具定义规范。
 * 对应 DSL 中的 tool("name") { ... } 块，
 * 也支持通过 @AgentTool 注解的 Java 方法。
 */
public class ToolSpec {

    private String name;
    private String description;
    private List<ParameterSpec> parameters = new ArrayList<>();
    private Object executeBody; // Groovy Closure 在运行时绑定

    // @AgentTool 注解扫描的方法引用
    private Object toolBean;
    private Method toolBeanMethod;

    public ToolSpec() {
    }

    public ToolSpec(String name) {
        this.name = name;
    }

    // --- Getters & Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ParameterSpec> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterSpec> parameters) {
        this.parameters = parameters;
    }

    public Object getExecuteBody() {
        return executeBody;
    }

    public void setExecuteBody(Object executeBody) {
        this.executeBody = executeBody;
    }

    public Object getToolBean() {
        return toolBean;
    }

    public Method getToolBeanMethod() {
        return toolBeanMethod;
    }

    /**
     * 设置工具的 Java 方法引用（用于 @AgentTool 注解扫描后的工具）。
     */
    public void setToolBeanMethod(Object bean, Method method) {
        this.toolBean = bean;
        this.toolBeanMethod = method;
    }

    /**
     * 判断此工具是否基于 Java 方法（而非 Groovy Closure）。
     */
    public boolean isBeanMethod() {
        return toolBean != null && toolBeanMethod != null;
    }

    @Override
    public String toString() {
        return "ToolSpec{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", parameters=" + parameters.size() +
                '}';
    }
}
