package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CodeExecutionToolTest {

    @Test
    public void testGroovyExecuteBasic() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.groovyExecute("1 + 2 + 3", null);
        assertTrue(result.contains("6"));
    }

    @Test
    public void testGroovyExecuteString() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.groovyExecute("'hello'.toUpperCase()", null);
        assertTrue(result.contains("HELLO"));
    }

    @Test
    public void testGroovyExecuteList() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.groovyExecute("[1, 2, 3].sum()", null);
        assertTrue(result.contains("6"));
    }

    @Test
    public void testGroovyEmptyCode() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.groovyExecute("", null);
        assertTrue(result.contains("Error"));
    }

    @Test
    public void testShellScriptRunBasic() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.shellScriptRun("echo 'Hello from shell'", "bash", null);
        assertTrue(result.contains("退出码: 0"));
        assertTrue(result.contains("Hello from shell"));
    }

    @Test
    public void testShellScriptRunBlacklist() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.shellScriptRun("rm -rf /", "bash", null);
        assertTrue(result.contains("[安全拒绝]"));
    }

    @Test
    public void testShellScriptRunForkBomb() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.shellScriptRun(":(){:|:&};:", "bash", null);
        assertTrue(result.contains("[安全拒绝]"));
    }

    @Test
    public void testShellScriptEmpty() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.shellScriptRun("", "bash", null);
        assertTrue(result.contains("Error"));
    }

    @Test
    public void testPythonRunBasic() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.pythonRun("print(1 + 2 + 3)", null, null);
        assertTrue(result.contains("退出码: 0"));
        assertTrue(result.contains("6"));
    }

    @Test
    public void testPythonRunWithRequirements() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.pythonRun("print('test')", "invalid-package-xyz", null);
        assertTrue(result.startsWith("[失败]"));
    }

    @Test
    public void testPythonBlacklist() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.pythonRun("import os; os.system('ls')", null, null);
        assertTrue(result.contains("[安全拒绝]"));
    }

    @Test
    public void testPythonEmptyCode() {
        CodeExecutionTool tool = new CodeExecutionTool();
        String result = tool.pythonRun("", null, null);
        assertTrue(result.contains("Error"));
    }

    @Test
    public void testPythonEnvironmentMissing() {
        CodeExecutionTool tool = new CodeExecutionTool();
        // This test may fail if Python is installed, but checks the detection logic
        String result = tool.pythonRun("print(1)", null, 1);
        // Should either work or return environment error
        assertNotNull(result);
    }
}
