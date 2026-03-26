package com.agentdsl.cli;

import com.agentdsl.compiler.Diagnostic;
import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.tools.BuiltinToolRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * {@code agentdsl validate} — 校验 DSL 脚本语法和语义。
 *
 * <h3>示例</h3>
 * 
 * <pre>
 *   agentdsl validate examples/simple-chat.agent.groovy
 *   agentdsl validate examples/tools-demo.agent.groovy --json
 * </pre>
 *
 * <h3>CI/CD 集成</h3>
 * <p>
 * 此命令适合加入 CI/CD 流水线以在部署前校验脚本：
 * 
 * <pre>
 *   # Makefile / GitHub Actions 示例
 *   java -jar agentdsl.jar validate agents/*.agent.groovy
 *   # 失败时 exit code = 1，CI 流水线会自动拦截
 * </pre>
 */
@Command(name = "validate", description = "校验 DSL 脚本的语法和语义（适合 CI/CD 集成）", mixinStandardHelpOptions = true)
public class ValidateCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "DSL 脚本文件路径 (.agent.groovy)")
    private Path scriptPath;

    @Option(names = { "--json" }, description = "以 JSON 格式输出校验结果（CI 工具友好）")
    private boolean jsonOutput;

    @Option(names = { "--sandbox" }, description = "启用安全沙箱校验（默认 false）", defaultValue = "false")
    private boolean sandbox;

    @Override
    public Integer call() {
        try {
            DslCompiler compiler = new DslCompiler(sandbox);
            Set<String> builtinToolNames = BuiltinToolRegistry.getBuiltinTools().stream()
                    .map(ToolSpec::getName)
                    .collect(Collectors.toSet());
            compiler.setKnownBuiltinToolNames(builtinToolNames);
            DslCompileResult result = compiler.compileFile(scriptPath);

            if (jsonOutput) {
                printJsonSuccess(result);
            } else {
                System.out.println("✅ 语法校验通过: " + scriptPath);
                System.out.printf("   发现 %d 个 Agent, %d 个工具, %d 个工作流%n",
                        result.getAgents().size(),
                        result.getTools().size(),
                        result.getWorkflows().size());

                if (result.getDiagnostics() != null && !result.getDiagnostics().isEmpty()) {
                    System.out.println("⚠️  Compilation Warnings:");
                    for (Diagnostic diag : result.getDiagnostics()) {
                        System.out.printf("  - [%s] %s%n", diag.getTarget(), diag.getMessage());
                    }
                }
            }
            return 0;

        } catch (DslCompilationException e) {
            if (jsonOutput) {
                printJsonError(e);
            } else {
                System.err.println("❌ 语法校验失败: " + scriptPath);
                System.err.println("   错误码: " + e.getErrorCode());
                System.err.println("   原因:   " + e.getMessage());
            }
            return 1;

        } catch (Exception e) {
            if (jsonOutput) {
                System.out.println("{\"valid\": false, \"error\": \"" + escape(e.getMessage()) + "\"}");
            } else {
                System.err.println("❌ 校验失败: " + e.getMessage());
            }
            return 1;
        }
    }

    private void printJsonSuccess(DslCompileResult result) {
        System.out.printf("""
                {
                  "valid": true,
                  "agents": %d,
                  "tools": %d,
                  "workflows": %d
                }%n""",
                result.getAgents().size(),
                result.getTools().size(),
                result.getWorkflows().size());
    }

    private void printJsonError(DslCompilationException e) {
        System.out.printf("""
                {
                  "valid": false,
                  "errorCode": "%s",
                  "error": "%s"
                }%n""",
                e.getErrorCode(),
                escape(e.getMessage()));
    }

    private static String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
