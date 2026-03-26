package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 统一多模态大模型对接工具。
 * 支持图片理解、音频转写、视频基础处理。
 */
public class MultiModalTool {
    private static final Logger log = LoggerFactory.getLogger(MultiModalTool.class);
    private final OkHttpClient client;
    private final Gson gson;

    public MultiModalTool() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.gson = new Gson();
    }

    private String getResolveKey(String apiKey) {
        if (apiKey != null && !apiKey.trim().isEmpty()) return apiKey.trim();
        String sysKey = System.getenv("AGENTDSL_MULTIMODAL_KEY");
        if (sysKey != null && !sysKey.trim().isEmpty()) return sysKey.trim();
        // 兼容旧环境变量
        if (System.getenv("DASHSCOPE_API_KEY") != null) return System.getenv("DASHSCOPE_API_KEY");
        return System.getenv("OPENAI_API_KEY");
    }

    private String getResolveEndpoint(String endpoint, String defaultUrl) {
        if (endpoint != null && !endpoint.trim().isEmpty()) return endpoint.trim();
        String sysEndpoint = System.getenv("AGENTDSL_MULTIMODAL_ENDPOINT");
        if (sysEndpoint != null && !sysEndpoint.trim().isEmpty()) return sysEndpoint.trim();
        return defaultUrl;
    }

    private String getResolveModel(String model, String defaultModel) {
        if (model != null && !model.trim().isEmpty()) return model.trim();
        String sysModel = System.getenv("AGENTDSL_MULTIMODAL_MODEL");
        if (sysModel != null && !sysModel.trim().isEmpty()) return sysModel.trim();
        return defaultModel;
    }

    @AgentTool(name = "vision_analyze", description = "读取一张本地图片或在线图片URL，询问视觉模型图片内容。返回识别得到的文本。")
    public String visionAnalyze(
            @ToolParam(name = "imageSource", description = "本地图片绝对路径或在线URL") String imageSource,
            @ToolParam(name = "instruction", description = "对图片的提问") String instruction,
            @ToolParam(name = "apiKey", description = "API Key，空则使用环境变量的 KEY", required = false) String apiKey,
            @ToolParam(name = "endpoint", description = "API端点URL，默认使用 OpenAI 兼容格式或阿里原生格式", required = false) String endpoint,
            @ToolParam(name = "model", description = "模型名称，默认 gpt-4o", required = false) String model) {

        String resolveKey = getResolveKey(apiKey);
        String resolveEndpoint = getResolveEndpoint(endpoint, "https://api.openai.com/v1/chat/completions");
        String resolveModel = getResolveModel(model, "gpt-4o");

        if (resolveKey == null || resolveKey.trim().isEmpty()) {
            return "Error: API Key is required.";
        }

        try {
            String imageUrl;
            if (imageSource.toLowerCase().startsWith("http://") || imageSource.toLowerCase().startsWith("https://")) {
                imageUrl = imageSource;
            } else {
                File imgFile = new File(imageSource);
                if (!imgFile.exists()) return "Error: Image file not found: " + imageSource;
                byte[] fileContent = Files.readAllBytes(imgFile.toPath());
                String base64Image = Base64.getEncoder().encodeToString(fileContent);
                String mime = "image/jpeg";
                if (imageSource.toLowerCase().endsWith(".png")) mime = "image/png";
                if (imageSource.toLowerCase().endsWith(".webp")) mime = "image/webp";
                if (imageSource.toLowerCase().endsWith(".gif")) mime = "image/gif";
                imageUrl = "data:" + mime + ";base64," + base64Image;
            }

            boolean isDashScopeNative = resolveEndpoint.contains("/api/v1/services/aigc/multimodal-generation");

            Map<String, Object> requestBodyMap = new HashMap<>();
            requestBodyMap.put("model", resolveModel);

            if (isDashScopeNative) {
                requestBodyMap.put("input", Map.of("messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of("image", imageUrl), Map.of("text", instruction))
                ))));
            } else {
                requestBodyMap.put("messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", instruction),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                        )
                )));
                requestBodyMap.put("max_tokens", 1000);
            }

            String jsonBody = gson.toJson(requestBodyMap);
            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(resolveEndpoint)
                    .addHeader("Authorization", "Bearer " + resolveKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) return "Error calling vision API: " + response.code() + " - " + respBody;

                Map<String, Object> parsed = gson.fromJson(respBody, Map.class);
                if (isDashScopeNative) {
                    Map<String, Object> output = (Map<String, Object>) parsed.get("output");
                    if (output != null) {
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                            Object content = msg.get("content");
                            if (content instanceof List) {
                                return (String) ((List<Map<String, Object>>) content).get(0).get("text");
                            } else if (content instanceof String) {
                                return (String) content;
                            }
                        }
                    }
                } else {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        return (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
                    }
                }
                return "Error: No valid content returned. Response: " + respBody;
            }
        } catch (Exception e) {
            log.error("Failed executing vision_analyze", e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "audio_recognize", description = "识别本地音频文件并返回转写的文本（调用 OpenAI Whisper 等兼容器口）。")
    public String audioRecognize(
            @ToolParam(name = "audioFile", description = "本地音频文件绝对路径（如 .mp3, .wav, .m4a）") String audioFile,
            @ToolParam(name = "apiKey", description = "API Key，空则使用环境变量", required = false) String apiKey,
            @ToolParam(name = "endpoint", description = "API端点URL，默认 https://api.openai.com/v1/audio/transcriptions", required = false) String endpoint,
            @ToolParam(name = "model", description = "模型名称，默认 whisper-1", required = false) String model) {
            
        String resolveKey = getResolveKey(apiKey);
        if (resolveKey == null || resolveKey.isEmpty()) return "Error: API Key is required.";
        String resolveEndpoint = getResolveEndpoint(endpoint, "https://api.openai.com/v1/audio/transcriptions");
        String resolveModel = getResolveModel(model, "whisper-1");

        File file = new File(audioFile);
        if (!file.exists()) return "Error: Audio file not found: " + audioFile;

        try {
            RequestBody fileBody = RequestBody.create(file, MediaType.parse("audio/mpeg"));
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getName(), fileBody)
                    .addFormDataPart("model", resolveModel)
                    .build();

            Request request = new Request.Builder()
                    .url(resolveEndpoint)
                    .addHeader("Authorization", "Bearer " + resolveKey)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) return "Error calling audio API: " + response.code() + " - " + respBody;

                Map<String, Object> parsed = gson.fromJson(respBody, Map.class);
                return (String) parsed.getOrDefault("text", "No text recognized.");
            }
        } catch (Exception e) {
            log.error("Failed executing audio_recognize", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @AgentTool(name = "video_analyze", description = "分析视频内容，暂不支持直传大视频。此工具将来可调用视频理解原生模型或抽帧分析。")
    public String videoAnalyze(
            @ToolParam(name = "videoSource", description = "视频链接或本地路径") String videoSource,
            @ToolParam(name = "instruction", description = "指令描述") String instruction) {
        // 当前视频大模型多依赖专有 SDK（如阿里云 OSS 直传等）。此占位方法提示功能演进。
        return "Video analysis is not fully implemented via standard API yet. Please process the video manually or extract frames to use vision_analyze.";
    }
}
