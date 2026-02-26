package com.agentdsl.runtime;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.StepSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import groovy.lang.Closure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

    public WorkflowExecutor(AgentExecutor agentExecutor, AgentRegistry registry) {
        this.agentExecutor = agentExecutor;
        this.registry = registry;
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
     * @return 执行结果
     */
    public WorkflowResult execute(WorkflowSpec workflow, String input) {
        log.info("[Workflow:{}] 开始执行, 输入: {}", workflow.getName(), truncate(input, 100));

        WorkflowContext ctx = new WorkflowContext(input);

        for (StepSpec step : workflow.getSteps()) {
            executeStep(step, ctx);
        }

        WorkflowResult result = ctx.toResult();
        log.info("[Workflow:{}] 执行完成, 结果: {}", workflow.getName(), result);
        return result;
    }

    /**
     * 根据步骤类型分派执行。
     */
    private void executeStep(StepSpec step, WorkflowContext ctx) {
        switch (step.getType()) {
            case SEQUENTIAL -> executeSequential(step, ctx);
            case PARALLEL -> executeParallel(step, ctx);
            case CONDITION -> executeCondition(step, ctx);
            case LOOP -> executeLoop(step, ctx);
        }
    }

    /**
     * 顺序执行单个步骤。
     */
    private void executeSequential(StepSpec step, WorkflowContext ctx) {
        String agentName = step.getAgentRef();
        log.debug("[Step:{}] 顺序执行, agent={}", step.getName(), agentName);

        // 1. 应用输入转换
        String input = applyInputTransform(step, ctx);

        // 2. 调用 Agent
        String output = agentExecutor.chat(agentName, input);

        // 3. 应用输出转换
        Object finalOutput = applyOutputTransform(step, output);

        // 4. 记录结果
        ctx.putStepResult(step.getName(), finalOutput);
        log.debug("[Step:{}] 完成, 输出: {}", step.getName(), truncate(finalOutput.toString(), 100));
    }

    /**
     * 并行执行多个步骤。
     */
    private void executeParallel(StepSpec step, WorkflowContext ctx) {
        List<StepSpec> parallelSteps = step.getParallelSteps();
        log.debug("[Parallel] 并行执行 {} 个步骤", parallelSteps.size());

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(parallelSteps.size(), Runtime.getRuntime().availableProcessors()));

        try {
            // 提交所有并行任务
            List<Future<ParallelStepResult>> futures = new ArrayList<>();
            for (StepSpec parallelStep : parallelSteps) {
                // 每个并行步骤共享相同的上下文输入，但独立保存结果
                final String input = applyInputTransform(parallelStep, ctx);
                futures.add(executor.submit(() -> {
                    String output = agentExecutor.chat(parallelStep.getAgentRef(), input);
                    Object finalOutput = applyOutputTransform(parallelStep, output);
                    return new ParallelStepResult(parallelStep.getName(), finalOutput);
                }));
            }

            // 收集结果
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
