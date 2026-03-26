package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 钉钉机器人 Webhook 工具。
 */
public class DingTalkTool {
    private static final Logger log = LoggerFactory.getLogger(DingTalkTool.class);
    private final OkHttpClient client;
    private final Gson gson;

    public DingTalkTool() {
        this.client = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build();
        this.gson = new Gson();
    }

    @AgentTool(name = "dingtalk_send_text", description = "向钉钉群机器人发送文本消息。")
    public String dingTalkSendText(
            @ToolParam(name = "content", description = "消息内容") String content,
            @ToolParam(name = "webhookUrl", description = "Webhook 地址，为空则读取环境变量 AGENTDSL_DINGTALK_WEBHOOK", required = false) String webhookUrl,
            @ToolParam(name = "secret", description = "加签密钥（可选，为空则读 AGENTDSL_DINGTALK_SECRET）", required = false) String secret,
            @ToolParam(name = "atMobiles", description = "被 @ 的人的手机号（逗号分隔）", required = false) String atMobiles,
            @ToolParam(name = "isAtAll", description = "是否 @ 所有人", required = false) Boolean isAtAll) {

        String url = (webhookUrl != null && !webhookUrl.isEmpty()) ? webhookUrl : System.getenv("AGENTDSL_DINGTALK_WEBHOOK");
        String sec = (secret != null && !secret.isEmpty()) ? secret : System.getenv("AGENTDSL_DINGTALK_SECRET");

        if (url == null || url.isEmpty()) return "Error: Webhook URL is required.";

        try {
            if (sec != null && !sec.isEmpty()) {
                long timestamp = System.currentTimeMillis();
                String stringToSign = timestamp + "\n" + sec;
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(sec.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
                String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), "UTF-8");
                url += (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
            }

            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("msgtype", "text");
            bodyMap.put("text", Map.of("content", content));
            
            Map<String, Object> atMap = new HashMap<>();
            if (atMobiles != null && !atMobiles.isEmpty()) {
                atMap.put("atMobiles", atMobiles.split(","));
            }
            if (isAtAll != null) {
                atMap.put("isAtAll", isAtAll);
            }
            if (!atMap.isEmpty()) {
                bodyMap.put("at", atMap);
            }

            RequestBody body = RequestBody.create(gson.toJson(bodyMap), MediaType.parse("application/json"));
            Request request = new Request.Builder().url(url).post(body).build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) return "Error calling DingTalk Webhook: " + response.code() + " - " + respBody;
                return "Successfully sent DingTalk message: " + respBody;
            }
        } catch (Exception e) {
            log.error("钉钉消息发送失败", e);
            return "Error: " + e.getMessage();
        }
    }
}
