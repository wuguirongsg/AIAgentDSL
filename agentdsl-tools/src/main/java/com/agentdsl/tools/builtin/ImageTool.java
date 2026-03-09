package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 图片识别/分析工具。
 * 允许代理向视觉模型发送图片及其描述需求，以理解图片内容。
 */
public class ImageTool {
    private static final Logger log = LoggerFactory.getLogger(ImageTool.class);
    private final OkHttpClient client;
    private final Gson gson;

    public ImageTool() {
        // 优化 OkHttpClient 配置，增加重试机制并明确协议
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionSpecs(List.of(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                .build();
        this.gson = new Gson();
    }

    @AgentTool(name = "image_recognize", description = "读取一张本地图片或在线图片URL，并询问视觉模型图片的内容。返回识别的文本信息。")
    public String imageRecognize(
            @ToolParam(name = "imageSource", description = "本地图片绝对路径或在线URL (以http开头)") String imageSource,
            @ToolParam(name = "instruction", description = "对图片的提问或指令，例如 '这张图片里有什么？' 或 '提取图中的文字'") String instruction,
            @ToolParam(name = "apiKey", description = "API Key (若为空则尝试使用 OPENAI_API_KEY 或 DASHSCOPE_API_KEY 环境变量)", required = false) String apiKey,
            @ToolParam(name = "endpoint", description = "API 端点URL (如阿里: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions)", required = false) String endpoint,
            @ToolParam(name = "model", description = "使用的视觉模型名称 (如: gpt-4o, qwen-vl-max, qwen-vl-plus)，默认 gpt-4o", required = false) String model) {

        String resolveKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey
                : (System.getenv("DASHSCOPE_API_KEY") != null ? System.getenv("DASHSCOPE_API_KEY")
                        : System.getenv("OPENAI_API_KEY"));

        String resolveEndpoint = (endpoint != null && !endpoint.isEmpty()) ? endpoint
                : "https://api.openai.com/v1/chat/completions";

        String resolveModel = (model != null && !model.isEmpty()) ? model : "gpt-4o";

        if (resolveKey == null || resolveKey.trim().isEmpty()) {
            return "Error: API Key is required. Please set DASHSCOPE_API_KEY or OPENAI_API_KEY.";
        }

        try {
            String imageUrl;
            if (imageSource.toLowerCase().startsWith("http://") || imageSource.toLowerCase().startsWith("https://")) {
                imageUrl = imageSource;
            } else {
                File imgFile = new File(imageSource);
                if (!imgFile.exists()) {
                    return "Error: Image file not found: " + imageSource;
                }
                byte[] fileContent = Files.readAllBytes(imgFile.toPath());
                String base64Image = Base64.getEncoder().encodeToString(fileContent);
                // Determine mime type based on extension
                String mime = "image/jpeg";
                if (imageSource.toLowerCase().endsWith(".png"))
                    mime = "image/png";
                if (imageSource.toLowerCase().endsWith(".webp"))
                    mime = "image/webp";
                if (imageSource.toLowerCase().endsWith(".gif"))
                    mime = "image/gif";

                imageUrl = "data:" + mime + ";base64," + base64Image;
            }

            boolean isDashScopeNative = resolveEndpoint.contains("/api/v1/services/aigc/multimodal-generation");

            Map<String, Object> requestBodyMap = new HashMap<>();
            requestBodyMap.put("model", resolveModel);

            if (isDashScopeNative) {
                // 千问原生格式：需要嵌套在 input 字段里，且 content 数组对象的 key 为 image/text
                Map<String, Object> qwenImg = new HashMap<>();
                qwenImg.put("image", imageUrl);
                Map<String, Object> qwenText = new HashMap<>();
                qwenText.put("text", instruction);

                Map<String, Object> qwenMsg = new HashMap<>();
                qwenMsg.put("role", "user");
                qwenMsg.put("content", List.of(qwenImg, qwenText));

                Map<String, Object> input = new HashMap<>();
                input.put("messages", List.of(qwenMsg));
                requestBodyMap.put("input", input);
            } else {
                // OpenAI 标准格式
                Map<String, Object> imageUrlObj = new HashMap<>();
                imageUrlObj.put("url", imageUrl);

                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", instruction);

                Map<String, Object> imageContent = new HashMap<>();
                imageContent.put("type", "image_url");
                imageContent.put("image_url", imageUrlObj);

                Map<String, Object> message = new HashMap<>();
                message.put("role", "user");
                message.put("content", List.of(textContent, imageContent));

                requestBodyMap.put("messages", List.of(message));
                requestBodyMap.put("max_tokens", 1000);
            }

            String jsonBody = gson.toJson(requestBodyMap);
            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(resolveEndpoint)
                    .addHeader("Authorization", "Bearer " + resolveKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "AgentDSL/1.0 (Built-in ImageTool)")
                    .post(body)
                    .build();

            log.info("Sending image recognition request (nativeMode={}) to {}", isDashScopeNative, resolveEndpoint);
            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("Image API error {}: {}", response.code(), respBody);
                    return "Error calling vision API: " + response.code() + " - " + respBody;
                }

                Map<String, Object> parsed = gson.fromJson(respBody, Map.class);

                if (isDashScopeNative) {
                    // 处理千问原生返回: 结果在 output.choices[0].message.content[0].text
                    Map<String, Object> output = (Map<String, Object>) parsed.get("output");
                    if (output != null) {
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> messageResp = (Map<String, Object>) choices.get(0).get("message");
                            Object content = messageResp.get("content");
                            if (content instanceof List) {
                                List<Map<String, Object>> contentList = (List<Map<String, Object>>) content;
                                if (!contentList.isEmpty()) {
                                    return (String) contentList.get(0).get("text");
                                }
                            } else if (content instanceof String) {
                                return (String) content;
                            }
                        }
                    }
                } else {
                    // 处理 OpenAI 兼容返回
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> messageResp = (Map<String, Object>) choices.get(0).get("message");
                        return (String) messageResp.get("content");
                    }
                }
                return "Error: No valid content returned from vision API. Response: " + respBody;
            }

        } catch (Exception e) {
            log.error("Failed executing image recognition: {}", imageSource, e);
            return "Error: " + e.getMessage();
        }
    }
}
