package com.agentdsl.runtime;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Workflow 步骤中 execute 闭包的委托上下文。
 * <p>
 * 当 step 使用 {@code execute { ctx -> ... }} 时，此对象作为闭包的 delegate 和参数，
 * 提供对工作流上下文数据的访问以及直接工具调用能力。
 * <p>
 * 使用方式（在 DSL 脚本中）:
 * <pre>
 *     step("fetch-data") {
 *         execute { ctx ->
 *             def data = ctx.toolCall("db_query", [sql: "SELECT * FROM orders"])
 *             return data
 *         }
 *     }
 * </pre>
 */
public class WorkflowExecutionContext {

    private final WorkflowContext workflowContext;
    private final BiFunction<String, Map<String, Object>, String> toolResolver;

    public WorkflowExecutionContext(WorkflowContext workflowContext,
            BiFunction<String, Map<String, Object>, String> toolResolver) {
        this.workflowContext = workflowContext;
        this.toolResolver = toolResolver;
    }

    /**
     * 获取上一步骤的输出。
     */
    public Object getLastOutput() {
        return workflowContext.getLastOutput();
    }

    /**
     * 获取指定步骤的输出结果。
     */
    public Object getStepResult(String stepName) {
        return workflowContext.getStepResult(stepName);
    }

    /**
     * 获取所有步骤结果的只读视图。
     */
    public Map<String, Object> getAllStepResults() {
        return workflowContext.getAllStepResults();
    }

    /**
     * 获取工作流的初始输入。
     */
    public String getInitialInput() {
        return workflowContext.getInitialInput();
    }

    /**
     * 在 execute 闭包中直接调用已注册的工具。
     *
     * @param toolName 工具名称（如 "http_get", "db_query" 等已注册工具）
     * @param params   工具参数 Map
     * @return 工具执行结果字符串
     */
    public String toolCall(String toolName, Map<String, Object> params) {
        if (toolResolver == null) {
            throw new IllegalStateException("toolCall 不可用：当前执行上下文未注入工具解析器");
        }
        return toolResolver.apply(toolName, params);
    }
}
