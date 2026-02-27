package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonTool 单元测试。
 */
class JsonToolTest {

    private final JsonTool jsonTool = new JsonTool();

    @Test
    @DisplayName("解析有效 JSON")
    void shouldParseValidJson() {
        String result = jsonTool.jsonParse("{\"name\":\"Alice\",\"age\":30}");
        assertNotNull(result);
        assertTrue(result.contains("Alice"));
        assertTrue(result.contains("30"));
    }

    @Test
    @DisplayName("解析无效 JSON 返回错误")
    void shouldHandleInvalidJson() {
        String result = jsonTool.jsonParse("not json");
        assertTrue(result.startsWith("Error:"), "无效 JSON 应返回错误");
    }

    @Test
    @DisplayName("查询嵌套对象属性")
    void shouldQueryNestedProperty() {
        String json = "{\"user\":{\"name\":\"Bob\",\"address\":{\"city\":\"Beijing\"}}}";
        assertEquals("Bob", jsonTool.jsonQuery(json, "user.name"));
        assertEquals("Beijing", jsonTool.jsonQuery(json, "user.address.city"));
    }

    @Test
    @DisplayName("查询数组元素")
    void shouldQueryArrayElement() {
        String json = "{\"items\":[{\"title\":\"A\"},{\"title\":\"B\"}]}";
        assertEquals("A", jsonTool.jsonQuery(json, "items.0.title"));
        assertEquals("B", jsonTool.jsonQuery(json, "items.1.title"));
    }

    @Test
    @DisplayName("查询不存在的路径返回 null")
    void shouldReturnNullForMissingPath() {
        String json = "{\"a\":1}";
        assertEquals("null", jsonTool.jsonQuery(json, "b"));
    }

    @Test
    @DisplayName("查询数组越界返回错误")
    void shouldHandleArrayOutOfBounds() {
        String json = "{\"items\":[1,2]}";
        String result = jsonTool.jsonQuery(json, "items.5");
        assertTrue(result.contains("Error:"), "越界应返回错误");
    }

    @Test
    @DisplayName("查询结果为对象时返回 JSON")
    void shouldReturnJsonForObjectResult() {
        String json = "{\"user\":{\"name\":\"Alice\"}}";
        String result = jsonTool.jsonQuery(json, "user");
        assertTrue(result.contains("Alice"));
    }
}
