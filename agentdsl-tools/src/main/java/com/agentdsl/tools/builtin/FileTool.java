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

    @AgentTool(name = "file_read_lines", description = "读取指定文件特定行范围的内容。行号从1开始，包含起始和结束行。如果endLine不填，则从startLine读到文件末尾。")
    public String fileReadLines(
            @ToolParam(name = "filePath", description = "文件路径") String filePath,
            @ToolParam(name = "startLine", description = "起始行号（从 1 开始计）") Integer startLine,
            @ToolParam(name = "endLine", description = "结束行号，不写则读取到末尾", required = false) Integer endLine) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";
            if (!Files.exists(path) || !Files.isRegularFile(path)) return "Error: File not found or not regular.";
            long size = Files.size(path);
            if (size > MAX_READ_SIZE) return "Error: File too large.";
            
            List<String> lines = Files.readAllLines(path);
            int start = (startLine != null && startLine > 0) ? startLine - 1 : 0;
            int end = (endLine != null && endLine > 0 && endLine <= lines.size()) ? endLine : lines.size();
            
            if (start >= lines.size()) return "";
            if (end < start) return "Error: endLine must be >= startLine";
            
            return String.join("\n", lines.subList(start, end));
        } catch (IOException e) {
            log.error("文件分段读取失败: {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "file_append", description = "在文件末尾追加内容。")
    public String fileAppend(
            @ToolParam(name = "filePath", description = "文件路径") String filePath,
            @ToolParam(name = "content", description = "追加的内容") String content) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";
            if (!Files.exists(path)) {
                Path parent = path.getParent();
                if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
                Files.createFile(path);
            }
            Files.writeString(path, content, java.nio.file.StandardOpenOption.APPEND);
            return "Successfully appended to " + path;
        } catch (IOException e) {
            log.error("文件追加失败: {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "file_insert", description = "在文件特定行数插入内容（该行及之后的内容向后移动）。行号从1起算。")
    public String fileInsert(
            @ToolParam(name = "filePath", description = "文件路径") String filePath,
            @ToolParam(name = "lineNumber", description = "插入位置的行号（从1起算）") Integer lineNumber,
            @ToolParam(name = "content", description = "插入的内容") String content) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";
            if (!Files.exists(path)) return "Error: File not found.";
            
            List<String> lines = Files.readAllLines(path);
            int index = (lineNumber != null && lineNumber > 0) ? lineNumber - 1 : 0;
            if (index > lines.size()) index = lines.size();
            
            lines.add(index, content);
            Files.write(path, lines);
            return "Successfully inserted at line " + (index + 1) + " in " + path;
        } catch (IOException e) {
            log.error("文件插入失败: {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "file_search", description = "在文件中全文搜索指定关键字或正则表达式，返回匹配行的行号及内容。")
    public String fileSearch(
            @ToolParam(name = "filePath", description = "文件路径") String filePath,
            @ToolParam(name = "keyword", description = "搜索的关键字或正则表达式") String keyword,
            @ToolParam(name = "useRegex", description = "是否使用正则表达式，默认false", required = false) Boolean useRegex) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";
            if (!Files.exists(path)) return "Error: File not found.";
            
            boolean regex = (useRegex != null && useRegex);
            java.util.regex.Pattern pattern = null;
            if (regex) {
                pattern = java.util.regex.Pattern.compile(keyword);
            }
            
            List<String> lines = Files.readAllLines(path);
            StringBuilder result = new StringBuilder();
            int limit = 100; // max matches to return
            int count = 0;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean match = false;
                if (regex) {
                    if (pattern.matcher(line).find()) match = true;
                } else {
                    if (line.contains(keyword)) match = true;
                }
                
                if (match) {
                    result.append(i + 1).append(": ").append(line).append("\n");
                    count++;
                    if (count >= limit) {
                        result.append("...(more than ").append(limit).append(" matches found, truncated)");
                        break;
                    }
                }
            }
            if (count == 0) return "No matches found.";
            return result.toString();
        } catch (Exception e) {
            log.error("文件搜索失败: {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "file_replace", description = "在文件中替换指定的文本（首个或全部匹配都会由String.replace处理全局替换）。")
    public String fileReplace(
            @ToolParam(name = "filePath", description = "文件路径") String filePath,
            @ToolParam(name = "target", description = "要替换的文本") String target,
            @ToolParam(name = "replacement", description = "替换成的新文本") String replacement) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";
            if (!Files.exists(path)) return "Error: File not found.";
            
            String content = Files.readString(path);
            if (!content.contains(target)) {
                return "Target string not found in file.";
            }
            String newContent = content.replace(target, replacement);
            Files.writeString(path, newContent);
            return "Successfully replaced occurrences in " + path;
        } catch (IOException e) {
            log.error("文件替换失败: {}", filePath, e);
            return "Error: " + e.getMessage();
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
