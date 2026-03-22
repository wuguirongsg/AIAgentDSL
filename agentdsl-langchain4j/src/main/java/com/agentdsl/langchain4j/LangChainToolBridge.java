package com.agentdsl.langchain4j;

import com.agentdsl.core.dsl.SkillExecutionContext;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * LangChain4j 工具桥接器。
 * 将 DSL 定义的 ToolSpec 转换为 LangChain4j 的 ToolSpecification + ToolExecutor。
 */
public class LangChainToolBridge {

    private static final Logger log = LoggerFactory.getLogger(LangChainToolBridge.class);

    /**
     * 工具调度解析器：允许 Logic Skill 的 execute 闭包通过 toolCall() 调用已注册的工具。
     */
    private BiFunction<String, Map<String, Object>, String> toolCallResolver;

    /**
     * 工具转换结果：包含规范和执行器。
     */
    public record ToolEntry(ToolSpecification specification, ToolExecutor executor) {
    }

    /**
     * 设置工具调度解析器。
     * 传入一个 (toolName, paramsMap) -> resultString 的函数，
     * 使得 Logic Skill 的 execute 闭包中可以调用 toolCall("http_get", [...])。
     */
    public void setToolCallResolver(BiFunction<String, Map<String, Object>, String> resolver) {
        this.toolCallResolver = resolver;
    }

    /**
     * 将 ToolSpec 转换为 LangChain4j 的 ToolEntry。
     */
    public ToolEntry convert(ToolSpec toolSpec) {
        log.info("转换工具: {}", toolSpec.getName());
        return convertDynamic(toolSpec, parsedParams -> {
            if (toolSpec.isBeanMethod()) {
                return invokeBeanMethod(toolSpec, parsedParams);
            }

            Object body = toolSpec.getExecuteBody();
            if (body instanceof Closure<?> closure) {
                if (toolCallResolver != null) {
                    SkillExecutionContext ctx = new SkillExecutionContext(toolCallResolver);
                    closure.setDelegate(ctx);
                    closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                }

                Object result = closure.getMaximumNumberOfParameters() == 0
                        ? closure.call()
                        : closure.call(parsedParams);
                return result != null ? result.toString() : "null";
            }
            return "Error: Tool execute body is not a Closure";
        });
    }

    public ToolEntry convertDynamic(String name,
            String description,
            List<ParameterSpec> parameters,
            Function<Map<String, Object>, String> executorFn) {
        ToolSpec toolSpec = new ToolSpec(name);
        toolSpec.setDescription(description);
        toolSpec.setParameters(parameters != null ? parameters : Collections.emptyList());
        return convertDynamic(toolSpec, executorFn);
    }

    private ToolEntry convertDynamic(ToolSpec toolSpec, Function<Map<String, Object>, String> executorFn) {
        ToolSpecification specification = buildSpecification(toolSpec);
        ToolExecutor executor = buildExecutor(toolSpec, executorFn);
        return new ToolEntry(specification, executor);
    }

    private ToolSpecification buildSpecification(ToolSpec toolSpec) {
        String desc = toolSpec.getDescription();
        if (toolSpec.getReturnDescription() != null && !toolSpec.getReturnDescription().isBlank()) {
            desc = desc != null ? desc + "\nReturns: " + toolSpec.getReturnDescription()
                    : "Returns: " + toolSpec.getReturnDescription();
        }

        var specBuilder = ToolSpecification.builder()
                .name(toolSpec.getName())
                .description(desc);

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

        return specBuilder.build();
    }

    private ToolExecutor buildExecutor(ToolSpec toolSpec, Function<Map<String, Object>, String> executorFn) {
        return (request, memoryId) -> {
            log.debug("执行工具 '{}', 参数: {}", toolSpec.getName(), request.arguments());

            Map<String, Object> parsedParams = parseArguments(request);
            String validationError = validateParameters(toolSpec, parsedParams);
            if (validationError != null) {
                com.agentdsl.core.metrics.MetricsCollector.getInstance().record(
                        new com.agentdsl.core.metrics.ToolMetrics(
                                toolSpec.getName(), 0, false, "ValidationError",
                                parsedParams.size(), 0));
                return "Error: " + validationError;
            }

            int timeout = toolSpec.getTimeoutSeconds() > 0 ? toolSpec.getTimeoutSeconds() : 30;
            long startMs = System.currentTimeMillis();

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return executorFn.apply(parsedParams);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                String result = future.get(timeout, TimeUnit.SECONDS);
                long durationMs = System.currentTimeMillis() - startMs;

                com.agentdsl.core.metrics.MetricsCollector.getInstance().record(
                        new com.agentdsl.core.metrics.ToolMetrics(
                                toolSpec.getName(), durationMs, true, null,
                                parsedParams.size(), result != null ? result.length() : 0));

                return result;
            } catch (TimeoutException e) {
                future.cancel(true);
                long durationMs = System.currentTimeMillis() - startMs;

                com.agentdsl.core.metrics.MetricsCollector.getInstance().record(
                        new com.agentdsl.core.metrics.ToolMetrics(
                                toolSpec.getName(), durationMs, false, "TimeoutException",
                                parsedParams.size(), 0));

                return handleTimeoutOrError(toolSpec,
                        "Timeout exception: Execution exceeded " + timeout + " seconds");
            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - startMs;
                String errorType = e.getCause() != null
                        ? e.getCause().getClass().getSimpleName()
                        : e.getClass().getSimpleName();

                com.agentdsl.core.metrics.MetricsCollector.getInstance().record(
                        new com.agentdsl.core.metrics.ToolMetrics(
                                toolSpec.getName(), durationMs, false, errorType,
                                parsedParams.size(), 0));

                log.error("工具 '{}' 执行失败", toolSpec.getName(),
                        e.getCause() != null ? e.getCause() : e);
                return handleTimeoutOrError(toolSpec,
                        "Error executing tool: " + (e.getCause() != null
                                ? e.getCause().getMessage()
                                : e.getMessage()));
            }
        };
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

    private String validateParameters(ToolSpec toolSpec, Map<String, Object> params) {
        if (toolSpec.getParameters() == null)
            return null;
        for (ParameterSpec paramSpec : toolSpec.getParameters()) {
            Object value = params.get(paramSpec.getName());

            if (value == null && paramSpec.getDefaultValue() != null) {
                params.put(paramSpec.getName(), paramSpec.getDefaultValue());
                value = paramSpec.getDefaultValue();
            }
            if (value == null && paramSpec.isRequired()) {
                return "Missing required parameter: " + paramSpec.getName();
            }
            if (value == null)
                continue;

            if (paramSpec.getPattern() != null && value instanceof String strVal) {
                if (!strVal.matches(paramSpec.getPattern())) {
                    return "Parameter '" + paramSpec.getName() + "' does not match pattern: " + paramSpec.getPattern();
                }
            }

            if (value instanceof Number numVal) {
                double doubleVal = numVal.doubleValue();
                if (paramSpec.getMin() != null && doubleVal < paramSpec.getMin()) {
                    return "Parameter '" + paramSpec.getName() + "' must be >= " + paramSpec.getMin();
                }
                if (paramSpec.getMax() != null && doubleVal > paramSpec.getMax()) {
                    return "Parameter '" + paramSpec.getName() + "' must be <= " + paramSpec.getMax();
                }
            }

            if (paramSpec.getEnumValues() != null && !paramSpec.getEnumValues().isBlank()) {
                List<String> enums = Arrays.asList(paramSpec.getEnumValues().split(","));
                if (!enums.contains(value.toString())) {
                    return "Parameter '" + paramSpec.getName() + "' must be one of: " + paramSpec.getEnumValues();
                }
            }
        }
        return null;
    }

    private String handleTimeoutOrError(ToolSpec toolSpec, String errorMessage) {
        if (toolSpec.getOnErrorHandler() instanceof Closure<?> handler) {
            try {
                Object res;
                if (handler.getMaximumNumberOfParameters() == 1) {
                    res = handler.call(errorMessage);
                } else {
                    res = handler.call();
                }
                return res != null ? res.toString() : errorMessage;
            } catch (Exception ex) {
                log.warn("onError handler failed for tool {}", toolSpec.getName(), ex);
            }
        }
        return "Error: " + errorMessage;
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
    private String invokeBeanMethod(ToolSpec toolSpec, Map<String, Object> parsedParams) {
        try {
            java.lang.reflect.Method method = toolSpec.getToolBeanMethod();
            Object bean = toolSpec.getToolBean();
            java.lang.reflect.Parameter[] methodParams = method.getParameters();
            java.util.List<com.agentdsl.core.spec.ParameterSpec> specParams = toolSpec.getParameters();

            Object[] args = new Object[methodParams.length];
            for (int i = 0; i < methodParams.length; i++) {
                String paramName = (specParams != null && i < specParams.size())
                        ? specParams.get(i).getName()
                        : methodParams[i].getName();
                Object value = parsedParams.get(paramName);
                args[i] = com.agentdsl.core.utils.ConvertUtils.convertArg(value, methodParams[i].getType());
            }

            Object result = method.invoke(bean, args);
            return result != null ? result.toString() : "null";
        } catch (Exception e) {
            log.error("工具 '{}' Bean 方法执行失败", toolSpec.getName(), e);
            return "Error executing tool: " + e.getMessage();
        }
    }
}
