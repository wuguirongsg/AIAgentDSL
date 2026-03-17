package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.Eval;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

/**
 * 代码执行工具。
 * 支持 Groovy、Shell/Bat/PowerShell、Python 三种脚本执行方式。
 *
 * <p>安全策略开关（优先级由高到低）：
 * <ol>
 *   <li>构造函数参数 {@code CodeExecutionTool(boolean securityEnabled)}</li>
 *   <li>系统属性 {@code -Dagentdsl.code.security.disabled=true}</li>
 *   <li>环境变量 {@code AGENTDSL_CODE_SECURITY_DISABLED=true}</li>
 * </ol>
 * 以上任一为 {@code true} 时，Shell/Python 安全黑名单检查将被跳过。
 */
public class CodeExecutionTool {
    private static final Logger log = LoggerFactory.getLogger(CodeExecutionTool.class);

    private static final int DEFAULT_GROOVY_TIMEOUT = 30;
    private static final int DEFAULT_SHELL_TIMEOUT = 60;
    private static final int DEFAULT_PYTHON_TIMEOUT = 120;

    /** 是否启用安全黑名单检查，默认 true。 */
    private final boolean securityEnabled;

    // 安全黑名单
    private static final List<String> SHELL_BLACKLIST = List.of(
        "rm -rf /", "rm -rf /*", "mkfs", "dd if=",
        ":(){:|:&};:", "wget.*\\|.*sh", "curl.*\\|.*sh",
        "chmod 777 /", "chown.* /", "shutdown", "reboot",
        "telnet", "nc -e", "bash -i"
    );

    private static final List<String> PYTHON_BLACKLIST = List.of(
        "os.system", "subprocess.call", "subprocess.run", "subprocess.Popen",
        "__import__('os')", "__import__('subprocess')",
        "open('/etc", "open('/home", "open('/root", "open('/proc",
        "socket.socket", "requests.post", "urllib.request"
    );

    /**
     * 默认构造函数：读取环境变量/系统属性决定安全策略。
     * 设置 {@code AGENTDSL_CODE_SECURITY_DISABLED=true} 可全局关闭安全检查。
     */
    public CodeExecutionTool() {
        this(!isSecurityDisabledByEnv());
    }

    /**
     * 显式指定安全策略。
     *
     * @param securityEnabled {@code true} 启用黑名单检查（推荐生产环境），
     *                        {@code false} 关闭检查（允许执行网络请求等受限操作）
     */
    public CodeExecutionTool(boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
        if (!securityEnabled) {
            log.warn("CodeExecutionTool 安全检查已关闭，请确保运行环境受信任！");
        }
    }

    /**
     * 检查环境变量或系统属性是否要求禁用安全检查。
     */
    private static boolean isSecurityDisabledByEnv() {
        String sysProp = System.getProperty("agentdsl.code.security.disabled", "false");
        String envVar = System.getenv("AGENTDSL_CODE_SECURITY_DISABLED");
        return "true".equalsIgnoreCase(sysProp) || "true".equalsIgnoreCase(envVar);
    }

    /**
     * 当前实例安全检查是否开启。
     */
    public boolean isSecurityEnabled() {
        return securityEnabled;
    }

    // ────────────────────────────────────────────────────────────────
    // Groovy 代码执行
    // ────────────────────────────────────────────────────────────────

    @AgentTool(
        name = "groovy_execute",
        description = "执行一段 Groovy 代码并返回结果。适合数据处理、计算、字符串操作等轻量任务。"
    )
    public String groovyExecute(
            @ToolParam(name = "code", description = "要执行的 Groovy 代码") 
            String code,
            @ToolParam(name = "timeout_seconds", description = "超时秒数，默认30秒", required = false) 
            Integer timeoutSeconds) {
        
        if (code == null || code.trim().isEmpty()) {
            return "Error: Groovy code cannot be empty";
        }

        int timeout = timeoutSeconds != null ? timeoutSeconds : DEFAULT_GROOVY_TIMEOUT;
        
        // 创建安全配置
        CompilerConfiguration config = createSecureCompilerConfiguration();
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Binding binding = new Binding();
            binding.setVariable("out", new StringBuilder());
            
            GroovyShell shell = new GroovyShell(binding, config);
            
            Future<String> future = executor.submit(() -> {
                Object result = Eval.me(code);
                return result != null ? result.toString() : "(无返回值)";
            });
            
            return future.get(timeout, TimeUnit.SECONDS);
            
        } catch (TimeoutException e) {
            return "[超时] Groovy 代码执行超过 " + timeout + " 秒，已强制终止";
        } catch (ExecutionException e) {
            return "[执行错误] " + e.getCause().getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[中断] 代码执行被中断";
        } catch (Exception e) {
            return "[异常] " + e.getMessage();
        } finally {
            executor.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Shell/Bat/PowerShell 脚本执行
    // ────────────────────────────────────────────────────────────────

    @AgentTool(
        name = "shell_script_run",
        description = "将脚本内容写入临时文件并执行。支持 bash/sh（Linux/Mac）、bat（Windows）、powershell。执行完成后返回标准输出和错误输出。"
    )
    public String shellScriptRun(
            @ToolParam(name = "script", description = "脚本内容") 
            String script,
            @ToolParam(name = "type", description = "脚本类型：bash、sh、bat、powershell。默认根据操作系统自动选择", required = false) 
            String type,
            @ToolParam(name = "timeout_seconds", description = "超时秒数，默认60秒", required = false) 
            Integer timeoutSeconds) {
        
        if (script == null || script.trim().isEmpty()) {
            return "Error: Script content cannot be empty";
        }

        // 安全检查
        String securityViolation = checkShellSecurity(script);
        if (securityViolation != null) {
            return "[安全拒绝] 脚本包含禁止的命令: " + securityViolation;
        }

        int timeout = timeoutSeconds != null ? timeoutSeconds : DEFAULT_SHELL_TIMEOUT;
        String scriptType = resolveScriptType(type);
        
        Path scriptFile = null;
        try {
            // 写入临时文件
            String suffix = getScriptSuffix(scriptType);
            scriptFile = Files.createTempFile(Path.of("/tmp"), "agentdsl_", suffix);
            Files.writeString(scriptFile, script);
            scriptFile.toFile().setExecutable(true);
            
            // 构建命令
            List<String> command = buildShellCommand(scriptType, scriptFile.toString());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File("/tmp"));
            pb.redirectErrorStream(false);
            
            log.info("Executing shell script: {}", scriptFile);
            Process process = pb.start();
            
            // 异步读取输出
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[超时] 脚本执行超过 " + timeout + " 秒，已强制终止";
            }
            
            int exitCode = process.exitValue();
            return formatScriptResult(exitCode, stdout, stderr);
            
        } catch (Exception e) {
            return "[执行失败] " + e.getMessage();
        } finally {
            if (scriptFile != null) {
                try {
                    Files.deleteIfExists(scriptFile);
                } catch (Exception ignored) {}
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Python 脚本执行
    // ────────────────────────────────────────────────────────────────

    @AgentTool(
        name = "python_run",
        description = "执行 Python 脚本。需要宿主机已安装 Python 3。可以选择安装第三方库（需要 pip 可用）。返回执行结果。"
    )
    public String pythonRun(
            @ToolParam(name = "code", description = "Python 代码内容") 
            String code,
            @ToolParam(name = "requirements", description = "需要安装的 pip 包，逗号分隔，如 'pandas,numpy'。可选", required = false) 
            String requirements,
            @ToolParam(name = "timeout_seconds", description = "超时秒数，默认120秒", required = false) 
            Integer timeoutSeconds) {
        
        if (code == null || code.trim().isEmpty()) {
            return "Error: Python code cannot be empty";
        }

        // 安全检查
        String securityViolation = checkPythonSecurity(code);
        if (securityViolation != null) {
            return "[安全拒绝] Python 代码包含禁止的操作: " + securityViolation;
        }

        // 检测 Python 环境
        String pythonCmd = detectPythonCommand();
        if (pythonCmd == null) {
            return "[环境缺失] 未检测到 Python 3，请先安装 Python 3 并确保在 PATH 中";
        }

        int timeout = timeoutSeconds != null ? timeoutSeconds : DEFAULT_PYTHON_TIMEOUT;
        
        Path scriptFile = null;
        try {
            // 安装依赖
            if (requirements != null && !requirements.isBlank()) {
                String installResult = installRequirements(pythonCmd, requirements);
                if (installResult.startsWith("[失败]")) {
                    return installResult;
                }
            }
            
            // 写入临时文件
            scriptFile = Files.createTempFile(Path.of("/tmp"), "agentdsl_py_", ".py");
            Files.writeString(scriptFile, code);
            
            // 执行脚本
            ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptFile.toString());
            pb.directory(new File("/tmp"));
            pb.environment().put("PYTHONPATH", "/tmp");
            
            log.info("Executing Python script: {}", scriptFile);
            Process process = pb.start();
            
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[超时] Python 脚本执行超过 " + timeout + " 秒，已强制终止";
            }
            
            return formatScriptResult(process.exitValue(), stdout, stderr);
            
        } catch (Exception e) {
            return "[执行失败] " + e.getMessage();
        } finally {
            if (scriptFile != null) {
                try {
                    Files.deleteIfExists(scriptFile);
                } catch (Exception ignored) {}
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 私有辅助方法
    // ────────────────────────────────────────────────────────────────

    private CompilerConfiguration createSecureCompilerConfiguration() {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass("groovy.lang.GroovyObjectSupport");
        return config;
    }

    private String resolveScriptType(String type) {
        if (type != null && !type.isBlank()) {
            return type.toLowerCase();
        }
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("windows") ? "powershell" : "bash";
    }

    private String getScriptSuffix(String type) {
        return switch (type) {
            case "bat" -> ".bat";
            case "powershell" -> ".ps1";
            default -> ".sh";
        };
    }

    private List<String> buildShellCommand(String type, String scriptPath) {
        return switch (type) {
            case "bat" -> List.of("cmd.exe", "/c", scriptPath);
            case "powershell" -> List.of("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptPath);
            default -> List.of("/bin/bash", scriptPath);
        };
    }

    private String detectPythonCommand() {
        for (String cmd : List.of("python3", "python")) {
            try {
                Process p = new ProcessBuilder(cmd, "--version").start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return cmd;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String checkShellSecurity(String script) {
        if (!securityEnabled) {
            return null;
        }
        String lower = script.toLowerCase();
        for (String blocked : SHELL_BLACKLIST) {
            if (lower.contains(blocked.toLowerCase())) {
                return blocked;
            }
        }
        return null;
    }

    private String checkPythonSecurity(String code) {
        if (!securityEnabled) {
            return null;
        }
        for (String blocked : PYTHON_BLACKLIST) {
            if (code.contains(blocked)) {
                return blocked;
            }
        }
        return null;
    }

    private String installRequirements(String pythonCmd, String requirements) {
        try {
            String[] packages = requirements.split(",");
            for (String pkg : packages) {
                ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd, "-m", "pip", "install", pkg.trim(), "-q"
                );
                Process p = pb.start();
                if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
                    return "[失败] pip install " + pkg.trim() + " 安装失败";
                }
            }
            return "[成功] 依赖安装完成";
        } catch (Exception e) {
            return "[失败] pip 安装异常: " + e.getMessage();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String formatScriptResult(int exitCode, String stdout, String stderr) {
        StringBuilder sb = new StringBuilder();
        sb.append("退出码: ").append(exitCode).append("\n");
        if (!stdout.isBlank()) {
            sb.append("输出:\n").append(stdout.trim());
        }
        if (!stderr.isBlank()) {
            sb.append("\n错误输出:\n").append(stderr.trim());
        }
        if (stdout.isBlank() && stderr.isBlank()) {
            sb.append("(无输出)");
        }
        return sb.toString();
    }
}
