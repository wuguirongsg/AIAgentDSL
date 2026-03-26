package com.agentdsl.tools.builtin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MultiModalToolTest {
    private final MultiModalTool tool = new MultiModalTool();
    
    @Test
    void testMissingKey() {
        String res = tool.visionAnalyze("http://example.com/a.jpg", "What?", null, null, null);
        if (System.getenv("AGENTDSL_MULTIMODAL_KEY") == null && System.getenv("OPENAI_API_KEY") == null && System.getenv("DASHSCOPE_API_KEY") == null) {
            assertTrue(res.startsWith("Error: API Key is required"));
        }
    }
    
    @Test
    void testAudioMissingKey() {
        String res = tool.audioRecognize("/tmp/a.mp3", null, null, null);
        if (System.getenv("AGENTDSL_MULTIMODAL_KEY") == null && System.getenv("OPENAI_API_KEY") == null && System.getenv("DASHSCOPE_API_KEY") == null) {
            assertTrue(res.startsWith("Error: API Key is required"));
        }
    }
}
