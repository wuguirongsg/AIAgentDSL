package com.agentdsl.runtime.skill;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.langchain4j.LangChainToolBridge;
import com.agentdsl.langchain4j.LangChainToolBridge.ToolEntry;
import dev.langchain4j.memory.ChatMemory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将 memory 插件暴露的 capability 对象桥接为 runtime 工具。
 * 这层封装掉所有反射细节，避免注册中心直接感知插件 capability 形状。
 */
public final class MemoryCapabilityBridge {

    private final LangChainToolBridge toolBridge;

    public MemoryCapabilityBridge(LangChainToolBridge toolBridge) {
        this.toolBridge = toolBridge;
    }

    public List<Object> readCapabilities(ChatMemory memory) {
        if (memory == null) {
            return List.of();
        }
        try {
            Method getCapabilities = memory.getClass().getMethod("getCapabilities");
            Object rawCapabilities = getCapabilities.invoke(memory);
            if (!(rawCapabilities instanceof Iterable<?> capabilities)) {
                return List.of();
            }

            List<Object> result = new ArrayList<>();
            for (Object capability : capabilities) {
                result.add(capability);
            }
            return List.copyOf(result);
        } catch (NoSuchMethodException ignored) {
            return List.of();
        } catch (Exception e) {
            throw new DslRuntimeException("ADSL-051",
                    "读取 memory capability 失败: " + e.getMessage(), e);
        }
    }

    public Optional<Object> findCapabilityByName(ChatMemory memory, String capabilityName) {
        return readCapabilities(memory).stream()
                .filter(capability -> capabilityName.equals(capabilityName(capability)))
                .findFirst();
    }

    public String capabilityName(Object capability) {
        try {
            return invokeString(capability, "getName");
        } catch (Exception e) {
            throw new DslRuntimeException("ADSL-051",
                    "读取 memory capability 名称失败: " + e.getMessage(), e);
        }
    }

    public ToolEntry convert(Object capability, String exposedName) {
        try {
            String description = invokeString(capability, "getDescription");
            List<ParameterSpec> parameters = readCapabilityParameters(capability);
            Method execute = capability.getClass().getMethod("execute", Map.class);

            return toolBridge.convertDynamic(exposedName, description, parameters, args -> {
                try {
                    Object result = execute.invoke(capability, args);
                    return result != null ? result.toString() : "null";
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (DslRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new DslRuntimeException("ADSL-051",
                    "转换 memory capability 失败: " + e.getMessage(), e);
        }
    }

    private List<ParameterSpec> readCapabilityParameters(Object capability) throws Exception {
        Method method = capability.getClass().getMethod("getParameters");
        Object raw = method.invoke(capability);
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }

        List<ParameterSpec> parameters = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            ParameterSpec parameter = new ParameterSpec();
            parameter.setName(stringValue(map.get("name")));
            if (map.get("type") != null) {
                parameter.setType(stringValue(map.get("type")));
            }
            parameter.setDescription(stringValue(map.get("description")));
            Object required = map.get("required");
            if (required instanceof Boolean bool) {
                parameter.setRequired(bool);
            } else if (required != null) {
                parameter.setRequired(Boolean.parseBoolean(required.toString()));
            }
            parameters.add(parameter);
        }
        return parameters;
    }

    private String invokeString(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return stringValue(method.invoke(target));
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
