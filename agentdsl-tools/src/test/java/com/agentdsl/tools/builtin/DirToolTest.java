package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DirToolTest {
    @Test
    void testDirOperations(@TempDir Path tempDir) throws Exception {
        DirTool dirTool = new DirTool(List.of(tempDir.toString()));
        
        // test create
        String newDir = tempDir.resolve("testdir").toString();
        String createRes = dirTool.dirCreate(newDir);
        assertTrue(createRes.contains("Successfully created"));
        assertTrue(Files.exists(tempDir.resolve("testdir")));
        
        // test list
        String listRes = dirTool.dirList(tempDir.toString());
        assertTrue(listRes.contains("testdir"));
        
        // test copy
        Path srcFile = tempDir.resolve("src.txt");
        Files.writeString(srcFile, "hello");
        String destFile = tempDir.resolve("dest.txt").toString();
        String copyRes = dirTool.fileCopy(srcFile.toString(), destFile);
        assertTrue(copyRes.contains("Successfully copied"));
        assertTrue(Files.exists(tempDir.resolve("dest.txt")));
        
        // test move
        String moveFile = tempDir.resolve("testdir/moved.txt").toString();
        String moveRes = dirTool.fileMove(destFile, moveFile);
        assertTrue(moveRes.contains("Successfully moved"));
        assertTrue(Files.exists(tempDir.resolve("testdir/moved.txt")));
        assertFalse(Files.exists(tempDir.resolve("dest.txt")));
    }
}
