package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 内置 HTTP 工具。
 * 提供 http_get 和 http_post 方法，使用 OkHttp 实现。
 */
public class HttpTool {

    private static final Logger log = LoggerFactory.getLogger(HttpTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_RESPONSE_LENGTH = 1024 * 1024; // 1MB
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;

    public HttpTool() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        this.gson = new Gson();
    }

    @AgentTool(name = "http_get", description = "发送 HTTP GET 请求并返回响应体")
    public String httpGet(
            @ToolParam(name = "url", description = "请求 URL") String url,
            @ToolParam(name = "headers", description = "请求头，JSON 格式，如 {\"Authorization\":\"Bearer xxx\"}", required = false) String headers) {
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url).get();
            applyHeaders(requestBuilder, headers);

            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                log.info("HTTP GET {} -> status {}", url, response.code());
                return truncateResponse(body, response.code());
            }
        } catch (Exception e) {
            log.error("HTTP GET 请求失败: {}", url, e);
            return "Error: HTTP GET failed - " + e.getMessage();
        }
    }

    @AgentTool(name = "http_post", description = "发送 HTTP POST 请求并返回响应体")
    public String httpPost(
            @ToolParam(name = "url", description = "请求 URL") String url,
            @ToolParam(name = "body", description = "请求体内容") String body,
            @ToolParam(name = "contentType", description = "Content-Type，默认 application/json", required = false) String contentType) {
        try {
            MediaType mediaType = (contentType != null && !contentType.isBlank())
                    ? MediaType.parse(contentType)
                    : JSON_MEDIA_TYPE;

            RequestBody requestBody = RequestBody.create(body != null ? body : "", mediaType);
            Request request = new Request.Builder().url(url).post(requestBody).build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                log.info("HTTP POST {} -> status {}", url, response.code());
                return truncateResponse(responseBody, response.code());
            }
        } catch (Exception e) {
            log.error("HTTP POST 请求失败: {}", url, e);
            return "Error: HTTP POST failed - " + e.getMessage();
        }
    }

    private void applyHeaders(Request.Builder builder, String headersJson) {
        if (headersJson == null || headersJson.isBlank())
            return;
        try {
            Type type = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> headerMap = gson.fromJson(headersJson, type);
            if (headerMap != null) {
                for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("解析请求头 JSON 失败: {}", headersJson, e);
        }
    }

    private String truncateResponse(String body, int statusCode) {
        if (body == null || body.isEmpty())
            return "Status: " + statusCode + ", Body: (empty)";
        if (body.length() > MAX_RESPONSE_LENGTH) {
            return "Status: " + statusCode + ", Body (truncated to 1MB): " +
                    body.substring(0, MAX_RESPONSE_LENGTH) + "...[truncated]";
        }
        return body;
    }
}
