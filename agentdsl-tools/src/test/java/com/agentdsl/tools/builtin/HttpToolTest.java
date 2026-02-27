package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpTool 单元测试。
 * 注意：部分测试依赖网络，可能在离线环境失败。
 */
class HttpToolTest {

    private final HttpTool httpTool = new HttpTool();

    @Test
    @DisplayName("GET 请求 — 正常返回")
    void shouldGetSuccessfully() {
        // 使用 httpbin.org 进行真实 HTTP 测试
        String result = httpTool.httpGet("https://httpbin.org/get", null);

        // httpbin.org/get 返回 JSON，包含 "url" 字段
        assertNotNull(result);
        assertFalse(result.startsWith("Error:"), "不应返回错误: " + result);
        assertTrue(result.contains("httpbin.org"), "响应应包含 httpbin.org");
    }

    @Test
    @DisplayName("GET 请求 — 无效 URL 返回错误")
    void shouldHandleInvalidUrl() {
        String result = httpTool.httpGet("not-a-valid-url", null);
        assertTrue(result.startsWith("Error:"), "无效 URL 应返回错误");
    }

    @Test
    @DisplayName("POST 请求 — 正常返回")
    void shouldPostSuccessfully() {
        String body = "{\"key\":\"value\"}";
        String result = httpTool.httpPost("https://httpbin.org/post", body, "application/json");

        assertNotNull(result);
        assertFalse(result.startsWith("Error:"), "不应返回错误: " + result);
    }

    @Test
    @DisplayName("POST 请求 — 默认 content-type 为 JSON")
    void shouldUseDefaultContentType() {
        String body = "{\"test\":true}";
        String result = httpTool.httpPost("https://httpbin.org/post", body, null);

        assertNotNull(result);
        assertFalse(result.startsWith("Error:"), "不应返回错误: " + result);
    }
}
