package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 目录操作工具。
 * 提供 dir_list, dir_create, file_move, file_copy 等功能。带白名单控制。
 */
public class DirTool {

    private static final Logger log = LoggerFactory.getLogger(DirTool.class);
    private final List<String> allowedDirectories;

    public DirTool() {
        this(List.of("/tmp"));
    }

    public DirTool(List<String> allowedDirectories) {
        this.allowedDirectories = allowedDirectories;
    }

    @AgentTool(name = "dir_list", description = "列出指定目录下的文件和子目录。仅限白名单受控目录。")
    public String dirList(@ToolParam(name = "dirPath", description = "目录路径") String dirPath) {
        try {
            Path path = Paths.get(dirPath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";
            if (!Files.exists(path) || !Files.isDirectory(path)) return "Error: Directory not found.";
            
            try (Stream<Path> stream = Files.list(path)) {
                String result = stream
                        .map(p -> p.getFileName().toString() + (Files.isDirectory(p) ? "/" : ""))
                        .collect(Collectors.joining("\n"));
                return result.isEmpty() ? "(Empty directory)" : result;
            }
        } catch (IOException e) {
            log.error("列出目录失败: {}", dirPath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "dir_create", description = "创建新目录（支持级联创建）。仅限白名单受控目录。")
    public String dirCreate(@ToolParam(name = "dirPath", description = "目录路径") String dirPath) {
        try {
            Path path = Paths.get(dirPath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";
            
            Files.createDirectories(path);
            return "Successfully created directory " + path;
        } catch (IOException e) {
            log.error("创建目录失败: {}", dirPath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "file_move", description = "移动或重命名文件/目录。源文件和目标文件都必须在白名单受控目录中。")
    public String fileMove(
            @ToolParam(name = "srcPath", description = "源路径") String srcPath,
            @ToolParam(name = "destPath", description = "目标路径") String destPath) {
        try {
            Path src = Paths.get(srcPath).toAbsolutePath().normalize();
            Path dest = Paths.get(destPath).toAbsolutePath().normalize();
            
            if (!isAllowed(src) || !isAllowed(dest)) return "Error: Access denied.";
            if (!Files.exists(src)) return "Error: Source not found.";
            
            Path parent = dest.getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
            
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return "Successfully moved " + src + " to " + dest;
        } catch (IOException e) {
            log.error("文件移动失败: {} -> {}", srcPath, destPath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "file_copy", description = "复制文件/目录（目录仅复制空文件夹层）到目标路径。")
    public String fileCopy(
            @ToolParam(name = "srcPath", description = "源路径") String srcPath,
            @ToolParam(name = "destPath", description = "目标路径") String destPath) {
        try {
            Path src = Paths.get(srcPath).toAbsolutePath().normalize();
            Path dest = Paths.get(destPath).toAbsolutePath().normalize();
            
            if (!isAllowed(src) || !isAllowed(dest)) return "Error: Access denied.";
            if (!Files.exists(src)) return "Error: Source not found.";
            
            Path parent = dest.getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
            
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return "Successfully copied " + src + " to " + dest;
        } catch (IOException e) {
            log.error("文件复制失败: {} -> {}", srcPath, destPath, e);
            return "Error: " + e.getMessage();
        }
    }

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
