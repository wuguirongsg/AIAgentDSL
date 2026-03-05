package com.agentdsl.tools;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具扫描器。
 * 扫描指定类中标注了 {@link AgentTool} 的方法，将其转换为 {@link ToolSpec}。
 *
 * <pre>
 * List&lt;ToolSpec&gt; tools = ToolScanner.scan(myToolBean);
 * registry.registerTools(tools);
 * </pre>
 */
public class ToolScanner {

    private static final Logger log = LoggerFactory.getLogger(ToolScanner.class);

    /**
     * 扫描对象中标注了 @AgentTool 的方法，转换为 ToolSpec 列表。
     *
     * @param toolBean 包含 @AgentTool 方法的对象
     * @return ToolSpec 列表
     */
    public static List<ToolSpec> scan(Object toolBean) {
        List<ToolSpec> result = new ArrayList<>();
        Class<?> clazz = toolBean.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            AgentTool annotation = method.getAnnotation(AgentTool.class);
            if (annotation == null) {
                continue;
            }

            String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();
            String description = annotation.description();

            ToolSpec spec = new ToolSpec(toolName);
            spec.setDescription(description);

            // 解析参数
            List<ParameterSpec> params = new ArrayList<>();
            Parameter[] methodParams = method.getParameters();
            for (Parameter param : methodParams) {
                ParameterSpec paramSpec = new ParameterSpec();
                ToolParam toolParam = param.getAnnotation(ToolParam.class);

                if (toolParam != null && !toolParam.name().isEmpty()) {
                    paramSpec.setName(toolParam.name());
                } else {
                    paramSpec.setName(param.getName());
                }
                paramSpec.setType(mapJavaType(param.getType()));

                if (toolParam != null) {
                    paramSpec.setDescription(toolParam.description());
                    paramSpec.setRequired(toolParam.required());
                } else {
                    paramSpec.setRequired(true);
                }

                params.add(paramSpec);
            }
            spec.setParameters(params);

            // 将方法调用包装为 Groovy Closure 风格的执行体
            // 这里存储方法引用和目标对象，在 LangChainToolBridge 中调用
            spec.setToolBeanMethod(toolBean, method);

            log.info("扫描到工具: {} (方法: {}.{})", toolName, clazz.getSimpleName(), method.getName());
            result.add(spec);
        }

        return result;
    }

    /**
     * 将 Java 类型映射为 DSL 参数类型字符串。
     */
    private static String mapJavaType(Class<?> type) {
        if (type == String.class)
            return "string";
        if (type == int.class || type == Integer.class)
            return "integer";
        if (type == long.class || type == Long.class)
            return "integer";
        if (type == double.class || type == Double.class)
            return "number";
        if (type == float.class || type == Float.class)
            return "number";
        if (type == boolean.class || type == Boolean.class)
            return "boolean";
        if (List.class.isAssignableFrom(type))
            return "array";
        if (java.util.Map.class.isAssignableFrom(type))
            return "object";
        return "string"; // 默认
    }
}
