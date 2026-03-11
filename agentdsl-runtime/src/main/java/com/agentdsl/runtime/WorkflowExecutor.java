package com.agentdsl.runtime;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.StepSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import com.agentdsl.runtime.metrics.ExecutionTrace;
import com.agentdsl.runtime.metrics.StepTrace;
import groovy.lang.Closure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 工作流执行引擎。
 * 支持顺序、并行、条件路由、循环四种执行模式。
 */
public class WorkflowExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutor.class);

    private final AgentExecutor agentExecutor;
    private final AgentRegistry registry;
    private final int maxParallelThreads;

    public WorkflowExecutor(AgentExecutor agentExecutor, AgentRegistry registry) {
        this(agentExecutor, registry, Runtime.getRuntime().availableProcessors());
    }

    /**
     * @param maxParallelThreads 并行步骤最大线程数
     */
    public WorkflowExecutor(AgentExecutor agentExecutor, AgentRegistry registry, int maxParallelThreads) {
        this.agentExecutor = agentExecutor;
        this.registry = registry;
        this.maxParallelThreads = maxParallelThreads > 0 ? maxParallelThreads
                : Runtime.getRuntime().availableProcessors();
    }

    /**
     * 执行指定名称的工作流。
     *
     * @param workflowName 工作流名称
     * @param input        初始输入
     * @return 执行结果
     */
    public WorkflowResult execute(String workflowName, String input) {
        WorkflowSpec workflow = registry.getWorkflow(workflowName);
        return execute(workflow, input);
    }

    /**
     * 执行工作流。
     *
     * @param workflow 工作流规范
     * @param input    初始输入
     * @return 执行结果（含 ExecutionTrace）
     */
    public WorkflowResult execute(WorkflowSpec workflow, String input) {
        log.info("[Workflow:{}] 开始执行, 输入: {}", workflow.getName(), truncate(input, 100));

        WorkflowContext ctx = new WorkflowContext(input);

        // 创建执行追踪对象，注入到上下文
        //
        // TODO [TRACE-EXT-3] 如果对接 OpenTelemetry，在此创建根 Span：
        // Span rootSpan = tracer.spanBuilder("workflow." +
        // workflow.getName()).startSpan();
        // 并将 rootSpan 和 executionTrace 一起注入到 WorkflowContext
        ExecutionTrace trace = new ExecutionTrace(workflow.getName());
        ctx.setExecutionTrace(trace);

        String finalStatus = "completed";
        try {
            for (StepSpec step : workflow.getSteps()) {
                executeStep(step, ctx);
            }
        } catch (Exception e) {
            finalStatus = "failed";
            trace.complete("failed");
            log.error("[Workflow:{}] 执行失败", workflow.getName(), e);
            throw e;
        }

        trace.complete(finalStatus);
        WorkflowResult result = ctx.toResult();
        log.info("[Workflow:{}] 执行完成, 耐时={}ms, 结果: {}",
                workflow.getName(), trace.getTotalDurationMs(), result);
        return result;
    }

    private void executeStep(StepSpec step, WorkflowContext ctx) {
        long stepStart = System.currentTimeMillis();
        if (com.agentdsl.core.metrics.DebugTracer.isEnabled()) {
            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("stepType", step.getType().name());
            details.put("agentRef", step.getAgentRef());
            com.agentdsl.core.metrics.DebugTracer.record(com.agentdsl.core.metrics.DebugEvent.Type.WORKFLOW_STEP_START,
                    step.getName(), details);
            com.agentdsl.core.metrics.DebugTracer.enter();
        }

        try {
            switch (step.getType()) {
                case SEQUENTIAL -> executeSequential(step, ctx);
                case PARALLEL -> executeParallel(step, ctx);
                case CONDITION -> executeCondition(step, ctx);
                case LOOP -> executeLoop(step, ctx);
            }
        } finally {
            if (com.agentdsl.core.metrics.DebugTracer.isEnabled()) {
                com.agentdsl.core.metrics.DebugTracer.exit();
                Object output = ctx.getStepResult(step.getName());
                // Handle Parallel/Condition/Loop which might not set single step result
                if (output == null && step.getType() != com.agentdsl.core.spec.StepSpec.StepType.SEQUENTIAL) {
                    output = ctx.getLastOutput();
                }
                long duration = System.currentTimeMillis() - stepStart;
                com.agentdsl.core.metrics.DebugTracer.record(
                        com.agentdsl.core.metrics.DebugEvent.Type.WORKFLOW_STEP_END, step.getName(),
                        java.util.Map.of("output", output != null ? output : "null", "durationMs", duration));
            }
        }
    }

    /**
     * 顺序执行单个步骤，根据执行模式分发到对应处理逻辑。
     * 支持五种执行模式：agent / execute / tool / skill / mcp
     */
    private void executeSequential(StepSpec step, WorkflowContext ctx) {
        String executionMode = step.getExecutionMode();
        log.debug("[Step:{}] 顺序执行, mode={}", step.getName(), executionMode);

        long stepStart = System.currentTimeMillis();
        String inputSummary = truncate(
                ctx.getLastOutput() != null ? ctx.getLastOutput().toString() : "", 200);

        String stepStatus = "completed";
        Object finalOutput;
        try {
            if (step.getExecuteClosure() != null) {
                finalOutput = executeCodeBlock(step, ctx);
            } else if (step.getToolRef() != null) {
                finalOutput = executeDirectToolCall(step, ctx);
            } else if (step.getSkillRef() != null) {
                finalOutput = executeDirectSkillCall(step, ctx);
            } else if (step.getMcpServerRef() != null) {
                finalOutput = executeDirectMcpCall(step, ctx);
            } else if (step.getAgentRef() != null) {
                finalOutput = executeAgentCall(step, ctx);
            } else {
                throw new DslRuntimeException("ADSL-031",
                        "步骤 '" + step.getName() + "' 未指定执行模式(agent/execute/tool/skill/mcp)");
            }

            ctx.putStepResult(step.getName(), finalOutput);
        } catch (Exception e) {
            stepStatus = "failed";
            long durationMs = System.currentTimeMillis() - stepStart;
            recordStepTrace(ctx, step.getName(), executionMode, inputSummary, "ERROR", durationMs, stepStatus);
            throw e;
        }

        long durationMs = System.currentTimeMillis() - stepStart;
        String outputSummary = truncate(finalOutput != null ? finalOutput.toString() : "null", 200);
        recordStepTrace(ctx, step.getName(), executionMode, inputSummary, outputSummary, durationMs, stepStatus);
        log.debug("[Step:{}] 完成, mode={}, 耗时={}ms, 输出: {}", step.getName(), executionMode, durationMs,
                truncate(outputSummary, 100));
    }

    /**
     * 模式 1：纯代码执行（execute 闭包）。
     */
    private Object executeCodeBlock(StepSpec step, WorkflowContext ctx) {
        log.debug("[Step:{}] 执行代码块", step.getName());

        if (com.agentdsl.core.metrics.DebugTracer.isEnabled()) {
            com.agentdsl.core.metrics.DebugTracer.record(
                    com.agentdsl.core.metrics.DebugEvent.Type.CODE_EXECUTE,
                    step.getName(), java.util.Map.of("mode", "execute"));
        }

        WorkflowExecutionContext execCtx = new WorkflowExecutionContext(ctx, registry.getToolCallResolver());
        Closure<?> closure = step.getExecuteClosure();
        closure.setDelegate(execCtx);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);

        Object result = closure.getMaximumNumberOfParameters() == 0
                ? closure.call()
                : closure.call(execCtx);

        return applyOutputTransform(step, result != null ? result.toString() : "null");
    }

    /**
     * 模式 2：直接调用工具。
     */
    private Object executeDirectToolCall(StepSpec step, WorkflowContext ctx) {
        String toolName = step.getToolRef();
        log.debug("[Step:{}] 直接调用工具: {}", step.getName(), toolName);

        if (com.agentdsl.core.metrics.DebugTracer.isEnabled()) {
            com.agentdsl.core.metrics.DebugTracer.record(
                    com.agentdsl.core.metrics.DebugEvent.Type.DIRECT_TOOL_CALL,
                    step.getName(), java.util.Map.of("tool", toolName));
        }

        Map<String, Object> params = resolveParamsFromInput(step, ctx);
        String result = registry.executeToolDirectly(toolName, params);
        return applyOutputTransform(step, result);
    }

    /**
     * 模式 3：直接调用 Skill。
     */
    private Object executeDirectSkillCall(StepSpec step, WorkflowContext ctx) {
        String skillName = step.getSkillRef();
        log.debug("[Step:{}] 直接调用 Skill: {}", step.getName(), skillName);

        if (com.agentdsl.core.metrics.DebugTracer.isEnabled()) {
            com.agentdsl.core.metrics.DebugTracer.record(
                    com.agentdsl.core.metrics.DebugEvent.Type.DIRECT_SKILL_CALL,
                    step.getName(), java.util.Map.of("skill", skillName));
        }

        Map<String, Object> params = resolveParamsFromInput(step, ctx);
        String result = registry.executeSkillDirectly(skillName, params);
        return applyOutputTransform(step, result);
    }

    /**
     * 模式 4：直接调用 MCP 工具。
     * 暂使用 registry 中已注册的 MCP 工具执行器。
     */
    private Object executeDirectMcpCall(StepSpec step, WorkflowContext ctx) {
        String serverName = step.getMcpServerRef();
        String toolName = step.getMcpToolRef();
        log.debug("[Step:{}] 直接调用 MCP: {}/{}", step.getName(), serverName, toolName);

        if (com.agentdsl.core.metrics.DebugTracer.isEnabled()) {
            com.agentdsl.core.metrics.DebugTracer.record(
                    com.agentdsl.core.metrics.DebugEvent.Type.DIRECT_MCP_CALL,
                    step.getName(),
                    java.util.Map.of("mcpServer", serverName, "mcpTool", toolName));
        }

        Map<String, Object> params = resolveParamsFromInput(step, ctx);
        // MCP 工具在注册时以 toolName 为 key 存入 toolExecutors，直接按工具名调用
        String result = registry.executeToolDirectly(toolName, params);
        return applyOutputTransform(step, result);
    }

    /**
     * 模式 5：调用 Agent（现有逻辑）。
     */
    private Object executeAgentCall(StepSpec step, WorkflowContext ctx) {
        String agentName = step.getAgentRef();
        log.debug("[Step:{}] 调用 Agent: {}", step.getName(), agentName);

        String input = applyInputTransform(step, ctx);
        String output = agentExecutor.chat(agentName, input);
        return applyOutputTransform(step, output);
    }

    /**
     * 记录步骤追踪到 ExecutionTrace。
     *
     * <p>
     * TODO [TRACE-EXT-1] 如果对接 OpenTelemetry，在此为每个步骤创建子 Span
     */
    private void recordStepTrace(WorkflowContext ctx, String stepName, String agentName,
            String inputSummary, String outputSummary, long durationMs, String status) {
        ExecutionTrace trace = ctx.getExecutionTrace();
        if (trace != null) {
            trace.addStep(new StepTrace(stepName, agentName, inputSummary,
                    outputSummary, durationMs, status));
        }
    }

    /**
     * 并行执行多个步骤（支持所有执行模式）。
     */
    private void executeParallel(StepSpec step, WorkflowContext ctx) {
        List<StepSpec> parallelSteps = step.getParallelSteps();
        log.debug("[Parallel] 并行执行 {} 个步骤", parallelSteps.size());

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(parallelSteps.size(), maxParallelThreads));

        final boolean isTracing = com.agentdsl.core.metrics.DebugTracer.isEnabled();

        try {
            List<Future<ParallelStepResult>> futures = new ArrayList<>();
            for (StepSpec parallelStep : parallelSteps) {
                futures.add(executor.submit(() -> {
                    if (isTracing) {
                        com.agentdsl.core.metrics.DebugTracer.enable();
                    }
                    try {
                        Object finalOutput = executeParallelSingleStep(parallelStep, ctx);
                        return new ParallelStepResult(parallelStep.getName(), finalOutput);
                    } finally {
                        if (isTracing) {
                            com.agentdsl.core.metrics.DebugTracer.disable();
                        }
                    }
                }));
            }

            for (Future<ParallelStepResult> future : futures) {
                try {
                    ParallelStepResult result = future.get(5, TimeUnit.MINUTES);
                    ctx.putStepResult(result.stepName, result.output);
                } catch (TimeoutException e) {
                    throw new DslRuntimeException("ADSL-020", "并行步骤执行超时");
                } catch (ExecutionException e) {
                    throw new DslRuntimeException("ADSL-020",
                            "并行步骤执行失败: " + e.getCause().getMessage(), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DslRuntimeException("ADSL-020", "并行步骤执行被中断");
                }
            }

            log.debug("[Parallel] 所有步骤完成");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 执行单个并行步骤，支持所有执行模式。
     */
    private Object executeParallelSingleStep(StepSpec step, WorkflowContext ctx) {
        if (step.getExecuteClosure() != null) {
            return executeCodeBlock(step, ctx);
        } else if (step.getToolRef() != null) {
            return executeDirectToolCall(step, ctx);
        } else if (step.getSkillRef() != null) {
            return executeDirectSkillCall(step, ctx);
        } else if (step.getMcpServerRef() != null) {
            return executeDirectMcpCall(step, ctx);
        } else if (step.getAgentRef() != null) {
            String input = applyInputTransform(step, ctx);
            String output = agentExecutor.chat(step.getAgentRef(), input);
            return applyOutputTransform(step, output);
        } else {
            throw new DslRuntimeException("ADSL-031",
                    "并行步骤 '" + step.getName() + "' 未指定执行模式");
        }
    }

    /**
     * 条件路由执行。
     */
    private void executeCondition(StepSpec step, WorkflowContext ctx) {
        log.debug("[Condition] 评估条件");

        // 1. 执行 check 闭包获取条件值
        Closure<?> checkClosure = step.getCheckClosure();
        Object conditionValue = invokeClosure(checkClosure, ctx.getLastOutput());
        String conditionKey = conditionValue != null ? conditionValue.toString() : "";

        log.debug("[Condition] 条件值: {}", conditionKey);

        // 2. 查找匹配的分支
        Map<String, List<StepSpec>> branches = step.getBranches();
        List<StepSpec> branchSteps = branches.get(conditionKey);

        if (branchSteps == null) {
            // 尝试 default 分支
            branchSteps = branches.get("default");
        }

        if (branchSteps == null || branchSteps.isEmpty()) {
            log.warn("[Condition] 未找到匹配的分支: {}", conditionKey);
            return;
        }

        // 3. 执行匹配分支中的所有步骤
        log.debug("[Condition] 进入分支: {}, 步骤数: {}", conditionKey, branchSteps.size());
        for (StepSpec branchStep : branchSteps) {
            executeStep(branchStep, ctx);
        }
    }

    /**
     * 循环迭代执行。
     */
    private void executeLoop(StepSpec step, WorkflowContext ctx) {
        int maxIterations = step.getMaxIterations();
        Closure<?> untilClosure = step.getUntilClosure();
        List<StepSpec> loopBody = step.getLoopBody();

        log.debug("[Loop] 开始循环, maxIterations={}", maxIterations);

        for (int i = 0; i < maxIterations; i++) {
            log.debug("[Loop] 迭代 {}/{}", i + 1, maxIterations);

            for (StepSpec bodyStep : loopBody) {
                executeStep(bodyStep, ctx);

                // 检查 until 条件（在 until 声明位置之后的步骤执行前检查）
                // until 通常放在步骤之间，检查上一步的结果
            }

            // 每次迭代结束后检查 until 条件
            if (untilClosure != null) {
                Object result = invokeClosure(untilClosure, ctx.getLastOutput());
                if (Boolean.TRUE.equals(result)) {
                    log.debug("[Loop] until 条件满足, 停止循环");
                    break;
                }
            }
        }

        log.debug("[Loop] 循环结束");
    }

    /**
     * 从 input 闭包解析工具/Skill/MCP 参数。
     * input 闭包应返回 Map；如果返回其他类型，尝试包装为单参数 Map。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveParamsFromInput(StepSpec step, WorkflowContext ctx) {
        Closure<?> inputTransform = step.getInputTransform();
        if (inputTransform != null) {
            Object transformed = invokeClosure(inputTransform, ctx.getLastOutput());
            if (transformed instanceof Map) {
                return (Map<String, Object>) transformed;
            }
            if (transformed != null) {
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("input", transformed);
                return wrapper;
            }
        }
        // 无 input 闭包时，尝试将 lastOutput 作为参数
        Object lastOutput = ctx.getLastOutput();
        if (lastOutput instanceof Map) {
            return (Map<String, Object>) lastOutput;
        }
        if (lastOutput != null) {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("input", lastOutput);
            return wrapper;
        }
        return Collections.emptyMap();
    }

    /**
     * 应用输入转换：如果步骤定义了 input 闭包，用它转换上下文中的最新输出。
     */
    private String applyInputTransform(StepSpec step, WorkflowContext ctx) {
        Closure<?> inputTransform = step.getInputTransform();
        if (inputTransform != null) {
            Object transformed = invokeClosure(inputTransform, ctx.getLastOutput());
            return transformed != null ? transformed.toString() : "";
        }
        // 默认使用上一步的输出
        Object lastOutput = ctx.getLastOutput();
        return lastOutput != null ? lastOutput.toString() : "";
    }

    /**
     * 应用输出转换：如果步骤定义了 output 闭包，用它转换 Agent 的输出。
     */
    private Object applyOutputTransform(StepSpec step, String agentOutput) {
        Closure<?> outputTransform = step.getOutputTransform();
        if (outputTransform != null) {
            return invokeClosure(outputTransform, agentOutput);
        }
        return agentOutput;
    }

    /**
     * 安全调用 Groovy 闭包。
     */
    private Object invokeClosure(Closure<?> closure, Object arg) {
        try {
            if (closure.getMaximumNumberOfParameters() == 0) {
                return closure.call();
            }
            return closure.call(arg);
        } catch (Exception e) {
            log.error("闭包执行失败: {}", e.getMessage(), e);
            throw new DslRuntimeException("ADSL-030", "闭包执行失败: " + e.getMessage(), e);
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null)
            return "null";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * 并行步骤结果内部记录。
     */
    private record ParallelStepResult(String stepName, Object output) {
    }
}
