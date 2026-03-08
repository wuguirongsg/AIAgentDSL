package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 本地命令执行工具。
 * 允许执行宿主机的 Shell/CLI 命令。
 */
public class CmdTool {
    private static final Logger log = LoggerFactory.getLogger(CmdTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    // 简易黑名单机制，拦截危险命令。仅在纯本地测试时可以一定程度保护
    private static final String[] BLACKLIST_PREFIXES = {
            "rm -rf /", "rm -rf /*", "mkfs"
    };

    @AgentTool(name = "cmd_execute", description = "执行本地 Shell/CLI 命令，返回标准输出和错误输出。支持 MacOS(zsh/bash)/Linux(bash) 或 CMD 上下文")
    public String cmdExecute(
            @ToolParam(name = "command", description = "要执行的命令行代码，如 'ls -la' 或 'python my_script.py'") String command,
            @ToolParam(name = "workingDir", description = "执行命令的工作目录，可选，默认为当前目录", required = false) String workingDir) {

        if (command == null || command.trim().isEmpty()) {
            return "Error: Command cannot be empty";
        }

        for (String bad : BLACKLIST_PREFIXES) {
            if (command.trim().startsWith(bad)) {
                return "Error: Command is blacklisted for security reasons.";
            }
        }

        try {
            // MacOS/Linux 使用 sh -c 包装，Windows 可以使用 cmd.exe /c
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String[] processCmd = isWindows
                    ? new String[] { "cmd.exe", "/c", command }
                    : new String[] { "sh", "-c", command };

            ProcessBuilder pb = new ProcessBuilder(processCmd);
            pb.redirectErrorStream(true); // 合并 stdout 和 stderr

            if (workingDir != null && !workingDir.trim().isEmpty()) {
                File dir = new File(workingDir);
                if (dir.exists() && dir.isDirectory()) {
                    pb.directory(dir);
                } else {
                    return "Error: Working directory does not exist or is not a directory: " + workingDir;
                }
            }

            log.info("Executing cmd: {}", command);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return output.toString() + "\nError: Command timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return output.toString() + "\nCommand failed with exit code: " + exitCode;
            }

            return output.toString();
        } catch (Exception e) {
            log.error("Cmd Error: {}", command, e);
            return "Error executing command: " + e.getMessage();
        }
    }
}
