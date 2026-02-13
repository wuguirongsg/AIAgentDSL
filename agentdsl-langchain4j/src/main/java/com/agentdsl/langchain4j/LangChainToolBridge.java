package com.agentdsl.langchain4j;

import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.ToolSpec;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.service.tool.ToolExecutor;
import groovy.lang.Closure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * LangChain4j 工具桥接器。
 * 将 DSL 定义的 ToolSpec 转换为 LangChain4j 的 ToolSpecification + ToolExecutor。
 */
public class LangChainToolBridge {

    private static final Logger log = LoggerFactory.getLogger(LangChainToolBridge.class);

    /**
     * 工具转换结果：包含规范和执行器。
     */
    public record ToolEntry(ToolSpecification specification, ToolExecutor executor) {
    }

    /**
     * 将 ToolSpec 转换为 LangChain4j 的 ToolEntry。
     */
    public ToolEntry convert(ToolSpec toolSpec) {
        log.info("转换工具: {}", toolSpec.getName());

        // 1. 构建 ToolSpecification
        var specBuilder = ToolSpecification.builder()
                .name(toolSpec.getName())
                .description(toolSpec.getDescription());

        // 添加参数定义 — 使用 LangChain4j 1.x 的 JsonObjectSchema API
        List<ParameterSpec> params = toolSpec.getParameters();
        if (params != null && !params.isEmpty()) {
            Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
            List<String> requiredParams = new ArrayList<>();

            for (ParameterSpec param : params) {
                JsonSchemaElement schemaElement = mapParamType(param.getType());
                properties.put(param.getName(), schemaElement);
                if (param.isRequired()) {
                    requiredParams.add(param.getName());
                }
            }

            JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder()
                    .addProperties(properties);
            if (!requiredParams.isEmpty()) {
                schemaBuilder.required(requiredParams);
            }

            specBuilder.parameters(schemaBuilder.build());
        }

        ToolSpecification specification = specBuilder.build();

        // 2. 构建 ToolExecutor
        ToolExecutor executor = (request, memoryId) -> {
            log.debug("执行工具 '{}', 参数: {}", toolSpec.getName(), request.arguments());
            try {
                if (toolSpec.isBeanMethod()) {
                    // @AgentTool 注解的 Java 方法
                    return invokeBeanMethod(toolSpec, request);
                }

                Object body = toolSpec.getExecuteBody();
                if (body instanceof Closure<?> closure) {
                    // Groovy Closure
                    Map<String, Object> parsedParams = parseArguments(request);

                    Object result;
                    if (closure.getMaximumNumberOfParameters() == 0) {
                        result = closure.call();
                    } else {
                        result = closure.call(parsedParams);
                    }
                    return result != null ? result.toString() : "null";
                } else {
                    return "Error: Tool execute body is not a Closure";
                }
            } catch (Exception e) {
                log.error("工具 '{}' 执行失败", toolSpec.getName(), e);
                return "Error executing tool: " + e.getMessage();
            }
        };

        return new ToolEntry(specification, executor);
    }

    /**
     * 批量转换多个 ToolSpec。
     */
    public List<ToolEntry> convertAll(List<ToolSpec> toolSpecs) {
        List<ToolEntry> entries = new ArrayList<>();
        for (ToolSpec spec : toolSpecs) {
            entries.add(convert(spec));
        }
        return entries;
    }

    /**
     * 从 ToolExecutionRequest 中解析参数为 Map。
     */
    private Map<String, Object> parseArguments(ToolExecutionRequest request) {
        String args = request.arguments();
        if (args == null || args.isBlank() || args.equals("{}")) {
            return Collections.emptyMap();
        }
        try {
            // 使用 Groovy 的 JsonSlurper 解析 JSON 参数
            groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper();
            Object parsed = slurper.parseText(args);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                return map;
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("解析工具参数失败: {}", args, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 将 DSL 参数类型映射为 LangChain4j 1.x 的 JsonSchemaElement。
     */
    private JsonSchemaElement mapParamType(String dslType) {
        return switch (dslType != null ? dslType.toLowerCase() : "string") {
            case "integer", "int" -> JsonIntegerSchema.builder().build();
            case "double", "float", "number" -> JsonNumberSchema.builder().build();
            case "boolean", "bool" -> JsonBooleanSchema.builder().build();
            case "list", "array" -> JsonArraySchema.builder().build();
            case "map", "object" -> JsonObjectSchema.builder().build();
            default -> JsonStringSchema.builder().build();
        };
    }

    /**
     * 通过反射调用 @AgentTool 注解的 Java 方法。
     */
    private String invokeBeanMethod(ToolSpec toolSpec, ToolExecutionRequest request) {
        try {
            Map<String, Object> parsedParams = parseArguments(request);
            java.lang.reflect.Method method = toolSpec.getToolBeanMethod();
            Object bean = toolSpec.getToolBean();
            java.lang.reflect.Parameter[] methodParams = method.getParameters();

            Object[] args = new Object[methodParams.length];
            for (int i = 0; i < methodParams.length; i++) {
                String paramName = methodParams[i].getName();
                Object value = parsedParams.get(paramName);
                args[i] = convertArg(value, methodParams[i].getType());
            }

            Object result = method.invoke(bean, args);
            return result != null ? result.toString() : "null";
        } catch (Exception e) {
            log.error("工具 '{}' Bean 方法执行失败", toolSpec.getName(), e);
            return "Error executing tool: " + e.getMessage();
        }
    }

    /**
     * 基础类型转换。
     */
    private Object convertArg(Object value, Class<?> targetType) {
        if (value == null)
            return null;
        if (targetType.isAssignableFrom(value.getClass()))
            return value;
        String str = value.toString();
        if (targetType == int.class || targetType == Integer.class)
            return Integer.parseInt(str);
        if (targetType == long.class || targetType == Long.class)
            return Long.parseLong(str);
        if (targetType == double.class || targetType == Double.class)
            return Double.parseDouble(str);
        if (targetType == float.class || targetType == Float.class)
            return Float.parseFloat(str);
        if (targetType == boolean.class || targetType == Boolean.class)
            return Boolean.parseBoolean(str);
        return str;
    }
}
