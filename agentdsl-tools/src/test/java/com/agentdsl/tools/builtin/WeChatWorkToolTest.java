package com.agentdsl.tools.builtin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeChatWorkToolTest {
    private final WeChatWorkTool tool = new WeChatWorkTool();
    
    @Test
    void testMissingWebhook() {
        String res = tool.wechatWorkSendText("Hello", null, null, null);
        if (System.getenv("AGENTDSL_WECHAT_WEBHOOK") == null) {
            assertTrue(res.startsWith("Error: Webhook URL is required"));
        }
    }
}
