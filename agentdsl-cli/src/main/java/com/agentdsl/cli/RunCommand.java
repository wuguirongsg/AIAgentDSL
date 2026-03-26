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
 *   # 打印每次 LLM 调用的完整对话内容（调试 Prompt、观察上下文变化）
 *   agentdsl run examples/simple-chat.agent.groovy --chat "你好" --verbose
 *
 *   # 执行工作流
 *   agentdsl run examples/workflow-demo.agent.groovy \
 *       --workflow translate-pipeline \
 *       --input "人工智能正在改变世界"
 *
 *   # 显示执行追踪（耗时详情）
 *   agentdsl run examples/workflow-demo.agent.groovy --workflow my-flow --input "test" --trace
 *
 *   # 以自主模式执行 Agent
 *   agentdsl run examples/autonomous-demo.agent.groovy --agent PlanAgent --autonomous "帮我搜索最新的 AI 新闻"
 *
 *   # 显示自主模式的思考过程（问题解构、策略规划、监控分析）
 *   agentdsl run examples/autonomous-demo.agent.groovy --agent SmartAgent --autonomous "分析项目代码质量" --think
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

    @Option(
        names = { "--verbose", "-v" },
        description = "打印每次 LLM 调用的完整对话内容（消息列表和原始回复）",
        defaultValue = "false"
    )
    private boolean verbose;

    @Option(
        names = { "--think", "-t" },
        description = "输出 Pipeline 各阶段的思考过程（问题解构、策略规划、监控分析）",
        defaultValue = "false"
    )
    private boolean showThink;

    @Option(names = { "--autonomous", "--auto" }, 
            description = "以自主模式执行，指定任务目标描述（可省略，配合 -ic 进入交互模式）",
            arity = "0..1")
    private String autonomousGoal;

    @Option(names = { "--interactive", "-ic" }, description = "交互式多轮对话模式，从控制台读取输入（忽略 --chat）", defaultValue = "false")
    private boolean interactive;

    @Override
    public Integer call() {
        if (chatMessage == null && workflowName == null && autonomousGoal == null && !interactive) {
            System.err.println("❌ 请指定 --chat <消息> 或 --workflow <名称> --input <输入> 或 --autonomous <目标> 或 --interactive");
            System.err.println("   或使用 --autonomous -ic 进入自主模式交互对话");
            return 1;
        }

        if (chatMessage != null && interactive) {
            System.err.println("❌ --chat 和 --interactive 不能同时使用");
            return 1;
        }

        if (workflowName != null && interactive) {
            System.err.println("❌ --workflow 和 --interactive 不能同时使用");
            return 1;
        }

        if (chatMessage != null && autonomousGoal != null) {
            System.err.println("❌ --chat 和 --autonomous 不能同时使用");
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

            if (verbose) {
                LlmConversationPrinter printer = new LlmConversationPrinter();
                engine.getExecutor().setLlmCallListener(printer);
                engine.getAutonomousExecutor().setLlmCallListener(printer);
            }

            if (showThink) {
                engine.getAutonomousExecutor().setPipelineThoughtListener(
                    new com.agentdsl.runtime.autonomous.impl.ConsolePipelineThoughtListener()
                );
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

                // 自主模式交互执行 (--autonomous --interactive 或 --auto -ic)
                if (interactive && (autonomousGoal != null || isAutonomousAgentConfigured(engine))) {
                    String targetAgent = resolveTargetAgent(engine);
                    if (targetAgent == null)
                        return 1;

                    com.agentdsl.runtime.autonomous.AutonomousExecutor executor = 
                        new com.agentdsl.runtime.autonomous.AutonomousExecutor(
                            new com.agentdsl.runtime.autonomous.ConsoleUserInteraction(), 
                            engine.getRegistry());
                    com.agentdsl.runtime.AgentInstance instance = engine.getRegistry().get(targetAgent);
                    executor.executeInteractive(instance);
                    return 0;
                }

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

                if (interactive) {
                    String targetAgent = resolveTargetAgent(engine);
                    if (targetAgent == null)
                        return 1;

                    InteractiveSession session = new InteractiveSession(engine, targetAgent);
                    session.run();
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

    private boolean isAutonomousAgentConfigured(AgentDslEngine engine) {
        var registry = engine.getRegistry();
        for (String name : registry.getAgentNames()) {
            var instance = registry.get(name);
            if (instance != null && instance.getSpec().getAutonomous() != null) {
                return true;
            }
        }
        return false;
    }
}
