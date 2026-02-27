package com.agentdsl.tools;

import com.agentdsl.core.spec.ToolSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BuiltinToolRegistry 单元测试。
 */
class BuiltinToolRegistryTest {

    @Test
    @DisplayName("getBuiltinTools 应返回所有内置工具")
    void shouldReturnAllBuiltinTools() {
        List<ToolSpec> tools = BuiltinToolRegistry.getBuiltinTools();

        assertNotNull(tools);
        assertFalse(tools.isEmpty(), "应至少注册一个内置工具");

        Set<String> names = tools.stream()
                .map(ToolSpec::getName)
                .collect(Collectors.toSet());

        // 验证预期的工具名称
        assertTrue(names.contains("http_get"), "应包含 http_get");
        assertTrue(names.contains("http_post"), "应包含 http_post");
        assertTrue(names.contains("json_parse"), "应包含 json_parse");
        assertTrue(names.contains("json_query"), "应包含 json_query");
        assertTrue(names.contains("file_read"), "应包含 file_read");
        assertTrue(names.contains("file_write"), "应包含 file_write");
    }

    @Test
    @DisplayName("每个内置工具都有 description 和 bean 方法引用")
    void shouldHaveDescriptionAndBeanMethod() {
        List<ToolSpec> tools = BuiltinToolRegistry.getBuiltinTools();

        for (ToolSpec tool : tools) {
            assertNotNull(tool.getDescription(), tool.getName() + " 应有 description");
            assertFalse(tool.getDescription().isBlank(), tool.getName() + " description 不应为空");
            assertTrue(tool.isBeanMethod(), tool.getName() + " 应标记为 bean method");
        }
    }

    @Test
    @DisplayName("getBuiltinTools 多次调用返回相同实例（缓存）")
    void shouldReturnCachedInstance() {
        List<ToolSpec> first = BuiltinToolRegistry.getBuiltinTools();
        List<ToolSpec> second = BuiltinToolRegistry.getBuiltinTools();
        assertSame(first, second, "多次调用应返回同一缓存实例");
    }
}
