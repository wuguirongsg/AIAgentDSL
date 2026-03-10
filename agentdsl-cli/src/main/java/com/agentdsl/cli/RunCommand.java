package com.agentdsl.cli;

import com.agentdsl.compiler.Diagnostic;
import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.runtime.AgentDslEngine;
import com.agentdsl.runtime.WorkflowResult;
import com.agentdsl.runtime.autonomous.AutonomousResult;
import com.agentdsl.runtime.metrics.ExecutionTrace;
import com.agentdsl.runtime.metrics.StepTrace;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code agentdsl run} — 加载并执行 DSL 脚本。
 *
 * <h3>示例</h3>
 * 
 * <pre>
 *   # 向 Agent 发送消息
 *   agentdsl run examples/simple-chat.agent.groovy --chat "你好"
 *
 *   # 向指定名称的 Agent 发送消息
 *   agentdsl run examples/multi-agent.agent.groovy --agent translator --chat "Hello"
 *
 *   # 执行工作流
 *   agentdsl run examples/workflow.agent.groovy \
 *       --workflow translate-pipeline \
 *       --input "人工智能正在改变世界"
 *
 *   # 显示执行追踪（耗时详情）
 *   agentdsl run examples/workflow.agent.groovy --workflow my-flow --input "test" --trace
 *
 *   # 以自主模式执行 Agent
 *   agentdsl run examples/autonomous-agent.agent.groovy --autonomous "帮我搜索最新的 AI 新闻"
 * </pre>
 */
@Command(name = "run", description = "加载并执行 DSL 脚本（Agent 对话或工作流）", mixinStandardHelpOptions = true)
public class RunCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "DSL 脚本文件路径 (.agent.groovy)")
    private Path scriptPath;

    @Option(names = { "--chat", "-c" }, description = "向 Agent 发送的消息文本")
    private String chatMessage;

    @Option(names = { "--agent", "-a" }, description = "目标 Agent 名称（默认用脚本中的第一个 Agent）")
    private String agentName;

    @Option(names = { "--workflow", "-w" }, description = "要执行的工作流名称")
    private String workflowName;

    @Option(names = { "--input", "-i" }, description = "工作流初始输入文本")
    private String workflowInput;

    @Option(names = { "--sandbox" }, description = "启用安全沙箱（默认 false）", defaultValue = "false")
    private boolean sandbox;

    @Option(names = { "--trace" }, description = "输出工作流执行追踪（各步骤耗时）", defaultValue = "false")
    private boolean showTrace;

    @Option(names = { "--debug", "-d" }, description = "输出详细的调试级别追踪信息（包括模型 I/O、工具调用）", defaultValue = "false")
    private boolean debugModel;

    @Option(names = { "--autonomous", "--auto" }, description = "以自主模式执行，指定任务目标描述")
    private String autonomousGoal;

    @Override
    public Integer call() {
        if (chatMessage == null && workflowName == null && autonomousGoal == null) {
            System.err.println("❌ 请指定 --chat <消息> 或 --workflow <名称> --input <输入> 或 --autonomous <目标>");
            return 1;
        }

        try (AgentDslEngine engine = new AgentDslEngine(sandbox)) {
            DslCompileResult compileResult = engine.loadFile(scriptPath);

            // 打印编译诊断警告
            if (compileResult.getDiagnostics() != null && !compileResult.getDiagnostics().isEmpty()) {
                System.out.println("⚠️  DSL Compilation Warnings:");
                for (Diagnostic diag : compileResult.getDiagnostics()) {
                    System.out.printf("  - [%s] %s%n", diag.getTarget(), diag.getMessage());
                }
                System.out.println();
            }

            if (debugModel) {
                com.agentdsl.core.metrics.DebugTracer.enable();
            }

            try {
                // 执行工作流
                if (workflowName != null) {
                    String input = workflowInput != null ? workflowInput : "";

                    if (debugModel) {
                        com.agentdsl.core.metrics.DebugTracer.record(
                                com.agentdsl.core.metrics.DebugEvent.Type.WORKFLOW_START, workflowName,
                                java.util.Map.of("input", input), 0);
                        com.agentdsl.core.metrics.DebugTracer.enter();
                    }

                    long wfStart = System.currentTimeMillis();
                    WorkflowResult result = engine.executeWorkflow(workflowName, input);
                    long wfDuration = System.currentTimeMillis() - wfStart;

                    System.out.println(result.getFinalOutputAsString());

                    if (debugModel) {
                        com.agentdsl.core.metrics.DebugTracer.exit();
                        com.agentdsl.core.metrics.DebugTracer.record(
                                com.agentdsl.core.metrics.DebugEvent.Type.WORKFLOW_END, workflowName,
                                java.util.Map.of("status", "completed", "durationMs", wfDuration), 0);
                    }

                    // 显示执行追踪（各步骤耗时）
                    if (showTrace && !debugModel) {
                        printTrace(result.getExecutionTrace());
                    }
                    return 0;
                }

                // 自主模式执行
                if (autonomousGoal != null) {
                    String targetAgent = resolveTargetAgent(engine);
                    if (targetAgent == null)
                        return 1;

                    long autoStart = System.currentTimeMillis();
                    AutonomousResult autoResult = engine.executeAutonomous(targetAgent, autonomousGoal);
                    long autoDuration = System.currentTimeMillis() - autoStart;

                    System.out.println("\n" + "═".repeat(60));
                    System.out.println(autoResult.getFinalAnswer());
                    System.out.println("═".repeat(60));
                    System.out.printf("📊 执行了 %d 步，耗时 %dms，%s%n",
                            autoResult.getTotalSteps(), autoDuration,
                            autoResult.isCompleted() ? "✅ 目标已完成" : "⚠️ " + autoResult.getTerminationReason());
                    return 0;
                }

                String targetAgent = resolveTargetAgent(engine);
                if (targetAgent == null)
                    return 1;

                String reply = engine.chat(targetAgent, chatMessage);
                System.out.println(reply);
                return 0;
            } finally {
                if (debugModel) {
                    DebugTraceRenderer.render(com.agentdsl.core.metrics.DebugTracer.getEvents());
                    com.agentdsl.core.metrics.DebugTracer.clear();
                    com.agentdsl.core.metrics.DebugTracer.disable();
                }
            }

        } catch (Exception e) {
            System.err.println("❌ 执行失败: " + e.getMessage());
            return 1;
        }
    }

    /**
     * 打印 ExecutionTrace 中的步骤耗时信息。
     *
     * <p>
     * TODO [TRACE-EXT-5] 后续可对接 Grafana 仪表盘：将此追踪数据推送到 Loki / Prometheus
     */
    private void printTrace(ExecutionTrace trace) {
        if (trace == null) {
            System.err.println("  (无追踪信息)");
            return;
        }
        System.out.println();
        System.out.printf("📊 执行追踪 [%s] — 总耗时 %dms (%s)%n",
                trace.getWorkflowName(), trace.getTotalDurationMs(), trace.getStatus());
        for (StepTrace step : trace.getSteps()) {
            System.out.printf("   %-25s %-10s %dms%n",
                    step.getStepName(), "[" + step.getStatus() + "]", step.getDurationMs());
        }
    }

    /**
     * 解析目标 Agent 名称。
     * 优先使用 --agent 参数，否则取脚本中第一个 Agent。
     */
    private String resolveTargetAgent(AgentDslEngine engine) {
        if (agentName != null)
            return agentName;
        var registry = engine.getRegistry();
        var agentNames = registry.getAgentNames();
        if (agentNames.isEmpty()) {
            System.err.println("❌ 脚本中未找到任何 Agent");
            return null;
        }
        return agentNames.iterator().next();
    }
}
