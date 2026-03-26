package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WordToolTest {
    @Test
    void testWordReadWrite(@TempDir Path tempDir) {
        WordTool wordTool = new WordTool(List.of(tempDir.toString()));
        String docxPath = tempDir.resolve("test.docx").toString();
        
        // Write
        String writeRes = wordTool.wordWrite(docxPath, "Hello Word\nThis is a test");
        assertTrue(writeRes.contains("Successfully"));
        
        // Read
        String readRes = wordTool.wordRead(docxPath);
        assertTrue(readRes.contains("Hello Word"));
        assertTrue(readRes.contains("This is a test"));
    }
}
