package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 内置 HTTP 工具。
 * 提供 http_get, http_post, http_put, http_delete, http_upload, http_download 方法。
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

    @AgentTool(name = "http_get", description = "发送 HTTP GET 请求")
    public String httpGet(
            @ToolParam(name = "url", description = "请求 URL") String url,
            @ToolParam(name = "headers", description = "请求头，JSON 格式，如 {\"Authorization\":\"Bearer xxx\"}", required = false) String headers,
            @ToolParam(name = "includeDetails", description = "是否返回包含状态码和Headers的完整JSON，默认false", required = false) Boolean includeDetails) {
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url).get();
            applyHeaders(requestBuilder, headers);
            return executeAndFormat(requestBuilder.build(), includeDetails);
        } catch (Exception e) {
            log.error("HTTP GET 请求失败: {}", url, e);
            return "Error: HTTP GET failed - " + e.getMessage();
        }
    }

    @AgentTool(name = "http_post", description = "发送 HTTP POST 请求")
    public String httpPost(
            @ToolParam(name = "url", description = "请求 URL") String url,
            @ToolParam(name = "body", description = "请求体内容") String body,
            @ToolParam(name = "contentType", description = "Content-Type，默认 application/json", required = false) String contentType,
            @ToolParam(name = "headers", description = "请求头，JSON格式", required = false) String headers,
            @ToolParam(name = "includeDetails", description = "是否返回包含状态码和Headers的完整JSON，默认false", required = false) Boolean includeDetails) {
        try {
            MediaType mediaType = (contentType != null && !contentType.isBlank()) ? MediaType.parse(contentType) : JSON_MEDIA_TYPE;
            RequestBody requestBody = RequestBody.create(body != null ? body : "", mediaType);
            Request.Builder requestBuilder = new Request.Builder().url(url).post(requestBody);
            applyHeaders(requestBuilder, headers);
            return executeAndFormat(requestBuilder.build(), includeDetails);
        } catch (Exception e) {
            log.error("HTTP POST 请求失败: {}", url, e);
            return "Error: HTTP POST failed - " + e.getMessage();
        }
    }

    @AgentTool(name = "http_put", description = "发送 HTTP PUT 请求")
    public String httpPut(
            @ToolParam(name = "url", description = "请求 URL") String url,
            @ToolParam(name = "body", description = "请求体内容") String body,
            @ToolParam(name = "contentType", description = "Content-Type，默认 application/json", required = false) String contentType,
            @ToolParam(name = "headers", description = "请求头，JSON格式", required = false) String headers,
            @ToolParam(name = "includeDetails", description = "是否返回包含状态码和Headers的完整JSON，默认false", required = false) Boolean includeDetails) {
        try {
            MediaType mediaType = (contentType != null && !contentType.isBlank()) ? MediaType.parse(contentType) : JSON_MEDIA_TYPE;
            RequestBody requestBody = RequestBody.create(body != null ? body : "", mediaType);
            Request.Builder requestBuilder = new Request.Builder().url(url).put(requestBody);
            applyHeaders(requestBuilder, headers);
            return executeAndFormat(requestBuilder.build(), includeDetails);
        } catch (Exception e) {
            log.error("HTTP PUT 请求失败: {}", url, e);
            return "Error: HTTP PUT failed - " + e.getMessage();
        }
    }

    @AgentTool(name = "http_delete", description = "发送 HTTP DELETE 请求")
    public String httpDelete(
            @ToolParam(name = "url", description = "请求 URL") String url,
            @ToolParam(name = "headers", description = "请求头，JSON格式", required = false) String headers,
            @ToolParam(name = "includeDetails", description = "是否返回包含状态码和Headers的完整JSON，默认false", required = false) Boolean includeDetails) {
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url).delete();
            applyHeaders(requestBuilder, headers);
            return executeAndFormat(requestBuilder.build(), includeDetails);
        } catch (Exception e) {
            log.error("HTTP DELETE 请求失败: {}", url, e);
            return "Error: HTTP DELETE failed - " + e.getMessage();
        }
    }

    @AgentTool(name = "http_upload", description = "发送 HTTP multipart/form-data 文件上传请求")
    public String httpUpload(
            @ToolParam(name = "url", description = "请求 URL") String url,
            @ToolParam(name = "filePath", description = "要上传的本地文件路径") String filePath,
            @ToolParam(name = "fileParamName", description = "接收文件的表单字段名，默认 file", required = false) String fileParamName,
            @ToolParam(name = "headers", description = "附加请求头，JSON格式", required = false) String headers,
            @ToolParam(name = "includeDetails", description = "是否返回包含明细细节", required = false) Boolean includeDetails) {
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                return "Error: File not found - " + filePath;
            }
            
            String paramName = (fileParamName != null && !fileParamName.isBlank()) ? fileParamName : "file";
            RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
            
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(paramName, file.getName(), fileBody)
                    .build();
                    
            Request.Builder requestBuilder = new Request.Builder().url(url).post(requestBody);
            applyHeaders(requestBuilder, headers);
            return executeAndFormat(requestBuilder.build(), includeDetails);
        } catch (Exception e) {
            log.error("HTTP Upload 失败: {}", url, e);
            return "Error: HTTP Upload failed - " + e.getMessage();
        }
    }

    @AgentTool(name = "http_download", description = "下载文件并保存到本地指定路径。该接口不返回文件内容，只返回下载状态。")
    public String httpDownload(
            @ToolParam(name = "url", description = "文件下载 URL") String url,
            @ToolParam(name = "savePath", description = "保存到本地的绝对路径") String savePath,
            @ToolParam(name = "headers", description = "附加请求头，JSON格式", required = false) String headers) {
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url).get();
            applyHeaders(requestBuilder, headers);
            
            Path path = Paths.get(savePath).toAbsolutePath().normalize();
            File dest = path.toFile();
            if (dest.getParentFile() != null && !dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }

            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return "Error: HTTP Download failed with status " + response.code();
                }
                long downloaded = 0;
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        downloaded += len;
                    }
                }
                return "Successfully downloaded " + downloaded + " bytes to " + savePath;
            }
        } catch (Exception e) {
            log.error("HTTP Download 失败: {}", url, e);
            return "Error: HTTP Download failed - " + e.getMessage();
        }
    }

    private void applyHeaders(Request.Builder builder, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return;
        try {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
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
    
    private String executeAndFormat(Request request, Boolean includeDetails) throws IOException {
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            boolean details = includeDetails != null && includeDetails;
            String body = response.body() != null ? response.body().string() : "";
            int code = response.code();
            log.info("{} {} -> status {} ({}ms)", request.method(), request.url(), code, System.currentTimeMillis() - start);
            
            if (details) {
                Map<String, Object> resultMsg = new HashMap<>();
                resultMsg.put("status", code);
                
                Map<String, String> headersMap = new HashMap<>();
                for (String name : response.headers().names()) {
                    headersMap.put(name, response.header(name));
                }
                resultMsg.put("headers", headersMap);
                resultMsg.put("body", limitBodyLength(body));
                
                return gson.toJson(resultMsg);
            } else {
                return truncateResponse(body, code);
            }
        }
    }
    
    private String limitBodyLength(String body) {
        if (body == null) return "";
        if (body.length() > MAX_RESPONSE_LENGTH) {
            return body.substring(0, MAX_RESPONSE_LENGTH) + "...[truncated]";
        }
        return body;
    }

    private String truncateResponse(String body, int statusCode) {
        if (body == null || body.isEmpty()) return "Status: " + statusCode + ", Body: (empty)";
        if (body.length() > MAX_RESPONSE_LENGTH) {
            return "Status: " + statusCode + ", Body (truncated to 1MB): " +
                    body.substring(0, MAX_RESPONSE_LENGTH) + "...[truncated]";
        }
        return body;
    }
}
