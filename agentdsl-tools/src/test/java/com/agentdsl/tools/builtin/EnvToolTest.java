package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvToolTest {
    private final EnvTool envTool = new EnvTool();
    
    @Test
    void testGetOsInfo() {
        String result = envTool.getOsInfo();
        assertNotNull(result);
        assertTrue(result.contains("OS Name:"));
    }
    
    @Test
    void testGetHardwareInfo() {
        String result = envTool.getHardwareInfo();
        assertNotNull(result);
        assertTrue(result.contains("Available Processors") || result.startsWith("Error"));
    }
}
