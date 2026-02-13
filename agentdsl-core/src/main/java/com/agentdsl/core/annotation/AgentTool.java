package com.agentdsl.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Java 方法上，声明该方法为一个可被 Agent 调用的工具。
 *
 * <pre>
 * public class MyTools {
 *     @AgentTool(name = "getWeather", description = "查询天气")
 *     public String weather(@ToolParam(description = "城市名") String city) {
 *         return "晴天 25°C";
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {

    /**
     * 工具名称。默认取方法名。
     */
    String name() default "";

    /**
     * 工具描述。
     */
    String description() default "";
}
