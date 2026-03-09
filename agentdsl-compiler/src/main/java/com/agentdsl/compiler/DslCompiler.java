package com.agentdsl.compiler;

import com.agentdsl.core.dsl.DslBaseScript;
import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import com.agentdsl.core.spec.DataSourceSpec;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/**
 * DSL 编译器。
 * 将 .agent.groovy 脚本编译并执行，产出 AgentSpec 和 ToolSpec。
 */
public class DslCompiler {

    private static final Logger log = LoggerFactory.getLogger(DslCompiler.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private final GroovyShell shell;
    private final boolean sandboxEnabled;
    private final long timeoutSeconds;

    public DslCompiler() {
        this(false);
    }

    /**
     * @param enableSandbox 是否启用安全沙箱
     */
    public DslCompiler(boolean enableSandbox) {
        this(enableSandbox, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * @param enableSandbox  是否启用安全沙箱
     * @param timeoutSeconds 脚本执行超时（秒），仅沙箱模式生效
     */
    public DslCompiler(boolean enableSandbox, long timeoutSeconds) {
        this.sandboxEnabled = enableSandbox;
        this.timeoutSeconds = timeoutSeconds;

        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass(DslBaseScript.class.getName());

        // 默认导入
        ImportCustomizer imports = new ImportCustomizer();
        imports.addStarImports("com.agentdsl.core.spec");
        config.addCompilationCustomizers(imports);

        // 安全沙箱
        if (enableSandbox) {
            config.addCompilationCustomizers(createSecureCustomizer());
        }

        this.shell = new GroovyShell(
                Thread.currentThread().getContextClassLoader(),
                config);
    }

    /**
     * 编译 DSL 脚本字符串。
     */
    public DslCompileResult compile(String scriptContent) {
        return compile(scriptContent, "Script1.groovy");
    }

    /**
     * 编译 DSL 脚本字符串，并指定脚本名称。
     */
    public DslCompileResult compile(String scriptContent, String scriptName) {
        try {
            Script script = shell.parse(scriptContent, scriptName);

            // 沙箱模式下：带超时保护执行脚本
            if (sandboxEnabled) {
                runWithTimeout(script);
            } else {
                script.run();
            }

            if (script instanceof DslBaseScript dslScript) {
                List<AgentSpec> agents = dslScript.getAgents();
                List<ToolSpec> tools = dslScript.getStandaloneTools();
                List<WorkflowSpec> workflows = dslScript.getWorkflows();
                List<SkillSpec> skills = dslScript.getStandaloneSkills();
                List<DataSourceSpec> datasources = dslScript.getDatasources();

                // 校验
                List<Diagnostic> diagnostics = DslValidator.validateAll(agents, tools, workflows, skills);

                log.info("DSL 编译成功: {} agents, {} tools, {} workflows, {} skills, {} datasources, {} diagnostics",
                        agents.size(), tools.size(), workflows.size(), skills.size(), datasources.size(),
                        diagnostics.size());
                return new DslCompileResult(agents, tools, workflows, skills, datasources, diagnostics);
            } else {
                throw new DslCompilationException("ADSL-002",
                        "脚本未继承 DslBaseScript，请检查编译器配置");
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            int lineNum = -1;

            // 尝试从堆栈跟踪中提取特定脚本的行号
            for (StackTraceElement element : e.getStackTrace()) {
                if (scriptName.equals(element.getFileName())) {
                    lineNum = element.getLineNumber();
                    break;
                }
            }

            String locationInfo = (lineNum > 0) ? " (在脚本 " + scriptName + " 第 " + lineNum + " 行)" : "";

            if (e instanceof DslCompilationException dce) {
                if (errorMsg != null && !errorMsg.contains("在脚本")) {
                    throw new DslCompilationException(dce.getErrorCode(), dce.getMessage() + locationInfo,
                            dce.getCause() != null ? dce.getCause() : e);
                }
                throw dce;
            }

            throw new DslCompilationException("ADSL-002",
                    "DSL 脚本编译失败" + locationInfo + ": " + errorMsg, e);
        }
    }

    /**
     * 从文件路径编译。
     */
    public DslCompileResult compileFile(Path scriptPath) {
        try {
            String content = Files.readString(scriptPath);
            log.info("编译 DSL 文件: {}", scriptPath);
            return compile(content, scriptPath.getFileName().toString());
        } catch (IOException e) {
            throw new DslCompilationException("ADSL-002",
                    "无法读取 DSL 文件: " + scriptPath, e);
        }
    }

    /**
     * 带超时保护执行脚本。
     * 防止恶意脚本无限循环占用资源。
     */
    private void runWithTimeout(Script script) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "dsl-sandbox-runner");
            t.setDaemon(true);
            return t;
        });

        Future<?> future = executor.submit((Runnable) script::run);
        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new DslCompilationException("ADSL-003",
                    "DSL 脚本执行超时（" + timeoutSeconds + "秒），可能存在无限循环");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DslCompilationException dce) {
                throw dce;
            }
            throw new DslCompilationException("ADSL-002",
                    "DSL 脚本执行失败: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DslCompilationException("ADSL-002", "DSL 脚本执行被中断");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 创建安全 AST 自定义器。
     * 限制 DSL 脚本中可使用的 API。
     */
    private SecureASTCustomizer createSecureCustomizer() {
        SecureASTCustomizer secure = new SecureASTCustomizer();

        // 禁止直接的包导入（白名单通过 ImportCustomizer 已控制）
        secure.setPackageAllowed(false);

        // 禁止使用 System.exit / Runtime.exec 等危险调用
        secure.setReceiversBlackList(List.of(
                "java.lang.System",
                "java.lang.Runtime",
                "java.lang.ProcessBuilder",
                "java.lang.Thread",
                "java.io.File",
                "java.nio.file.Files",
                "java.net.URL",
                "java.net.HttpURLConnection",
                "java.net.Socket",
                "java.net.ServerSocket"));

        return secure;
    }
}
