package com.agentdsl.tools.builtin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DingTalkToolTest {
    private final DingTalkTool tool = new DingTalkTool();
    
    @Test
    void testMissingWebhook() {
        String res = tool.dingTalkSendText("Hello", null, null, null, false);
        if (System.getenv("AGENTDSL_DINGTALK_WEBHOOK") == null) {
            assertTrue(res.startsWith("Error: Webhook URL is required"));
        }
    }
}
