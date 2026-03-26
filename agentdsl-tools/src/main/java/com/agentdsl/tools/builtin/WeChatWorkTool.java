package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信机器人 Webhook 工具。
 */
public class WeChatWorkTool {
    private static final Logger log = LoggerFactory.getLogger(WeChatWorkTool.class);
    private final OkHttpClient client;
    private final Gson gson;

    public WeChatWorkTool() {
        this.client = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build();
        this.gson = new Gson();
    }

    @AgentTool(name = "wechat_work_send_text", description = "向企业微信群机器人发送文本消息。")
    public String wechatWorkSendText(
            @ToolParam(name = "content", description = "消息内容") String content,
            @ToolParam(name = "webhookUrl", description = "Webhook 地址，为空则读取环境变量 AGENTDSL_WECHAT_WEBHOOK", required = false) String webhookUrl,
            @ToolParam(name = "mentionedList", description = "提醒群成员列表（拼音或 userid，逗号分隔，@all表示所有人）", required = false) String mentionedList,
            @ToolParam(name = "mentionedMobileList", description = "提醒手机号列表（逗号分隔，@all表示所有人）", required = false) String mentionedMobileList) {

        String url = (webhookUrl != null && !webhookUrl.isEmpty()) ? webhookUrl : System.getenv("AGENTDSL_WECHAT_WEBHOOK");
        if (url == null || url.isEmpty()) return "Error: Webhook URL is required.";

        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("msgtype", "text");
            
            Map<String, Object> textMap = new HashMap<>();
            textMap.put("content", content);

            if (mentionedList != null && !mentionedList.isEmpty()) {
                textMap.put("mentioned_list", mentionedList.split(","));
            }
            if (mentionedMobileList != null && !mentionedMobileList.isEmpty()) {
                textMap.put("mentioned_mobile_list", mentionedMobileList.split(","));
            }

            bodyMap.put("text", textMap);

            RequestBody body = RequestBody.create(gson.toJson(bodyMap), MediaType.parse("application/json"));
            Request request = new Request.Builder().url(url).post(body).build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) return "Error calling WeChat Work Webhook: " + response.code() + " - " + respBody;
                return "Successfully sent WeChat Work message: " + respBody;
            }
        } catch (Exception e) {
            log.error("企业微信消息发送失败", e);
            return "Error: " + e.getMessage();
        }
    }
}
