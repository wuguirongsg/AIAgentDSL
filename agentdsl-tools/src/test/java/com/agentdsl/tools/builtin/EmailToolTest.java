package com.agentdsl.tools.builtin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailToolTest {
    private final EmailTool tool = new EmailTool();
    
    @Test
    void testMissingConfig() {
        String res = tool.emailSend("test@test.com", "Subject", "Body", null, null, null, null);
        if (System.getenv("AGENTDSL_SMTP_HOST") == null) {
            assertTrue(res.startsWith("Error: SMTP host"));
        }
    }
}
