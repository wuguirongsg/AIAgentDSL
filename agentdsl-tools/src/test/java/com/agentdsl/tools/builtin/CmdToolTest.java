package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CmdToolTest {
    @Test
    public void testCmdRun() {
        CmdTool cmdTool = new CmdTool();
        String result = cmdTool.cmdExecute("echo 'Hello AgentDSL'", null);

        System.out.println("Result: " + result);
        assertTrue(result.contains("Hello AgentDSL"));
    }

    @Test
    public void testBlacklist() {
        CmdTool cmdTool = new CmdTool();
        String result = cmdTool.cmdExecute("rm -rf /", null);
        assertTrue(result.contains("Error: Command is blacklisted for security reasons."));
    }
}
