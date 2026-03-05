package com.agentdsl.core.dsl;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Logic Skill 执行上下文。
 * <p>
 * 当 Logic Skill 的 {@code execute} 闭包被运行时，此对象会被设为闭包的 delegate，
 * 从而让闭包内部可以直接调用 {@code toolCall("toolName", [key: value])} 方法。
 * <p>
 * 使用方式（在 DSL 脚本中）:
 * 
 * <pre>
 *     skill("my-logic-skill") {
 *         type "logic"
 *         description "..."
 *         requires "http_get"
 *
 *         execute { params ->
 *             def response = toolCall("http_get", [url: "https://..."])
 *             return response
 *         }
 *     }
 * </pre>
 */
public class SkillExecutionContext {

    /**
     * 工具调度函数：接收 (toolName, paramsMap)，返回工具执行结果字符串。
     */
    private final BiFunction<String, Map<String, Object>, String> toolResolver;

    public SkillExecutionContext(BiFunction<String, Map<String, Object>, String> toolResolver) {
        this.toolResolver = toolResolver;
    }

    /**
     * 在 Logic Skill 的 execute 闭包中调用已注册的工具。
     *
     * @param toolName 工具名称（如 "http_get", "file_read" 等已注册工具）
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
