package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTool 单元测试。
 */
class FileToolTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("读取文件 — 白名单内正常读取")
    void shouldReadFileInAllowedDirectory() throws IOException {
        FileTool tool = new FileTool(List.of(tempDir.toString()));

        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, AgentDSL!");

        String result = tool.fileRead(testFile.toString());
        assertEquals("Hello, AgentDSL!", result);
    }

    @Test
    @DisplayName("读取文件 — 白名单外拒绝")
    void shouldDenyReadOutsideAllowedDirectory() {
        FileTool tool = new FileTool(List.of("/nonexistent-allowed-dir"));

        String result = tool.fileRead("/etc/passwd");
        assertTrue(result.startsWith("Error: Access denied"), "白名单外应拒绝: " + result);
    }

    @Test
    @DisplayName("读取文件 — 文件不存在")
    void shouldHandleNonExistentFile() {
        FileTool tool = new FileTool(List.of(tempDir.toString()));

        String result = tool.fileRead(tempDir.resolve("no-such-file.txt").toString());
        assertTrue(result.startsWith("Error: File not found"), "不存在的文件应报错");
    }

    @Test
    @DisplayName("写入文件 — 白名单内正常写入")
    void shouldWriteFileInAllowedDirectory() throws IOException {
        FileTool tool = new FileTool(List.of(tempDir.toString()));

        Path testFile = tempDir.resolve("output.txt");
        String result = tool.fileWrite(testFile.toString(), "Written by FileTool");

        assertTrue(result.contains("Successfully wrote"), "应返回成功消息");
        assertEquals("Written by FileTool", Files.readString(testFile));
    }

    @Test
    @DisplayName("写入文件 — 白名单外拒绝")
    void shouldDenyWriteOutsideAllowedDirectory() {
        FileTool tool = new FileTool(List.of("/nonexistent-allowed-dir"));

        String result = tool.fileWrite("/tmp/agentdsl-test-should-not-exist.txt", "data");
        assertTrue(result.startsWith("Error: Access denied"), "白名单外应拒绝写入");
    }

    @Test
    @DisplayName("写入文件 — 自动创建父目录")
    void shouldCreateParentDirectories() throws IOException {
        FileTool tool = new FileTool(List.of(tempDir.toString()));

        Path nested = tempDir.resolve("sub/dir/file.txt");
        String result = tool.fileWrite(nested.toString(), "nested content");

        assertTrue(result.contains("Successfully wrote"));
        assertEquals("nested content", Files.readString(nested));
    }

    @Test
    @DisplayName("读取超大文件 — 拒绝")
    void shouldDenyLargeFileRead() throws IOException {
        FileTool tool = new FileTool(List.of(tempDir.toString()));

        // 创建一个 > 100KB 的文件
        Path largeFile = tempDir.resolve("large.txt");
        byte[] data = new byte[101 * 1024];
        Files.write(largeFile, data);

        String result = tool.fileRead(largeFile.toString());
        assertTrue(result.startsWith("Error: File too large"), "大文件应拒绝: " + result);
    }
}
