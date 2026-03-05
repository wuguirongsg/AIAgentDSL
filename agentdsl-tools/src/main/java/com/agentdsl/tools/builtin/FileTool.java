package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 内置文件操作工具。
 * 提供 file_read 和 file_write 方法，带白名单访问控制。
 */
public class FileTool {

    private static final Logger log = LoggerFactory.getLogger(FileTool.class);
    private static final long MAX_READ_SIZE = 100 * 1024; // 100KB

    private final List<String> allowedDirectories;

    /**
     * 默认构造函数，白名单仅包含 /tmp。
     */
    public FileTool() {
        this(List.of("/tmp"));
    }

    /**
     * 自定义白名单目录。
     */
    public FileTool(List<String> allowedDirectories) {
        this.allowedDirectories = allowedDirectories;
    }

    @AgentTool(name = "file_read", description = "读取指定路径的文件内容。仅允许访问白名单目录下的文件。")
    public String fileRead(@ToolParam(name = "filePath", description = "文件路径") String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();

            if (!isAllowed(path)) {
                return "Error: Access denied. Path '" + path + "' is outside allowed directories: "
                        + allowedDirectories;
            }

            if (!Files.exists(path)) {
                return "Error: File not found: " + path;
            }

            if (!Files.isRegularFile(path)) {
                return "Error: Not a regular file: " + path;
            }

            long size = Files.size(path);
            if (size > MAX_READ_SIZE) {
                return "Error: File too large (" + size + " bytes). Maximum allowed: " + MAX_READ_SIZE + " bytes";
            }

            String content = Files.readString(path);
            log.info("读取文件: {} ({} bytes)", path, content.length());
            return content;
        } catch (IOException e) {
            log.error("文件读取失败: {}", filePath, e);
            return "Error: File read failed - " + e.getMessage();
        }
    }

    @AgentTool(name = "file_write", description = "将内容写入指定路径的文件。仅允许写入白名单目录下的文件。")
    public String fileWrite(
            @ToolParam(name = "filePath", description = "文件路径") String filePath,
            @ToolParam(name = "content", description = "要写入的内容") String content) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();

            if (!isAllowed(path)) {
                return "Error: Access denied. Path '" + path + "' is outside allowed directories: "
                        + allowedDirectories;
            }

            // 确保父目录存在
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, content != null ? content : "");
            log.info("写入文件: {} ({} bytes)", path, (content != null ? content.length() : 0));
            return "Successfully wrote " + (content != null ? content.length() : 0) + " bytes to " + path;
        } catch (IOException e) {
            log.error("文件写入失败: {}", filePath, e);
            return "Error: File write failed - " + e.getMessage();
        }
    }

    /**
     * 检查路径是否在白名单目录中。
     */
    private boolean isAllowed(Path path) {
        String normalizedPath = path.toAbsolutePath().normalize().toString();
        for (String allowed : allowedDirectories) {
            String normalizedAllowed = Paths.get(allowed).toAbsolutePath().normalize().toString();
            if (normalizedPath.startsWith(normalizedAllowed)) {
                return true;
            }
        }
        return false;
    }
}
