package com.agentdsl.runtime.autonomous;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.metrics.DebugEvent;
import com.agentdsl.core.metrics.DebugTracer;
import com.agentdsl.core.spec.AutonomousSpec;
import com.agentdsl.runtime.AgentInstance;
import com.agentdsl.runtime.AgentRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 自主执行器 — Sprint D 核心组件。
 * 驱动 ReAct（Reason + Act）循环，支持 plan 和 fast 两种执行模式。
 *
 * <h3>Plan 模式流程</h3>
 * 
 * <pre>
 * 1. PlannerEngine 生成执行计划
 * 2. UserInteraction 确认计划 (APPROVE / MODIFY / REJECT)
 * 3. 执行 ReAct 循环 (Act → Observe → Reflect)
 * 4. max_steps 超限时询问用户
 * </pre>
 *
 * <h3>Fast 模式流程</h3>
 * 
 * <pre>
 * 1. 直接进入 ReAct 循环
 * 2. max_steps 超限时询问用户
 * </pre>
 */
public class AutonomousExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutonomousExecutor.class);

    private final UserInteraction userInteraction;
    private final AgentRegistry registry;

    public AutonomousExecutor(UserInteraction userInteraction) {
        this(userInteraction, null);
    }

    public AutonomousExecutor(UserInteraction userInteraction, AgentRegistry registry) {
        this.userInteraction = userInteraction;
        this.registry = registry;
    }

    /**
     * 执行自主任务。
     *
     * @param instance 已注册的 Agent 实例
     * @param userGoal 用户目标描述
     * @return 执行结果
     */
    public AutonomousResult execute(AgentInstance instance, String userGoal) {
        AutonomousSpec config = instance.getSpec().getAutonomous();
        if (config == null) {
            throw new DslRuntimeException("ADSL-030",
                    "Agent '" + instance.getName() + "' 未配置 autonomous 模式");
        }

        log.info("[{}] 自主模式启动 (mode={}, max_steps={}), 目标: {}",
                instance.getName(), config.getExecutionMode(), config.getMaxSteps(), userGoal);

        if (DebugTracer.isEnabled()) {
            Map<String, Object> details = new HashMap<>();
            details.put("mode", config.getExecutionMode());
            details.put("maxSteps", config.getMaxSteps());
            details.put("goal", userGoal);
            DebugTracer.record(DebugEvent.Type.AGENT_START, instance.getName(), details);
            DebugTracer.enter();
        }

        try {
            if (config.isPlanMode()) {
                return executePlanMode(instance, userGoal, config);
            } else {
                return executeFastMode(instance, userGoal, config);
            }
        } finally {
            if (DebugTracer.isEnabled()) {
                DebugTracer.exit();
                DebugTracer.record(DebugEvent.Type.AGENT_END, instance.getName(),
                        Map.of("mode", config.getExecutionMode()));
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Plan 模式
    // ────────────────────────────────────────────────────────────────

    private AutonomousResult executePlanMode(AgentInstance instance, String userGoal, AutonomousSpec config) {
        ChatModel model = instance.getModel();
        List<ToolSpecification> tools = instance.hasTools()
                ? new ArrayList<>(instance.getToolSpecifications())
                : List.of();

        PlannerEngine planner = new PlannerEngine(model);

        // 1. 生成执行计划
        userInteraction.showProgress("🧠 正在分析任务并生成执行计划...");
        ExecutionPlan plan = planner.generatePlan(userGoal, tools, instance.getSpec().getSystemPrompt());
        log.info("[{}] 生成了 {} 步执行计划", instance.getName(), plan.getSteps().size());

        // 2. 计划确认循环
        int maxPlanRevisions = 3;
        for (int revision = 0; revision < maxPlanRevisions; revision++) {
            PlanFeedback feedback = userInteraction.confirmPlan(plan.toDisplayString());

            if (feedback.isApproved()) {
                userInteraction.showProgress("✅ 计划已确认，开始执行...\n");
                break;
            } else if (feedback.isModify()) {
                userInteraction.showProgress("🔄 根据您的建议重新规划...");
                plan = planner.revisePlan(plan, feedback.userInput(), tools, instance.getSpec().getSystemPrompt());
                log.info("[{}] 计划已修改 (第 {} 次修改)", instance.getName(), revision + 1);
                // 继续循环，再次展示修改后的计划
            } else {
                // REJECT
                log.info("[{}] 用户拒绝执行计划", instance.getName());
                AutonomousResult result = new AutonomousResult();
                result.setPlan(plan);
                result.setCompleted(false);
                result.setTerminationReason("用户取消执行");
                result.setFinalAnswer("执行已取消。");
                return result;
            }
        }

        // 3. 执行 ReAct 循环
        AutonomousResult result = executeReActLoop(instance, userGoal, config);
        result.setPlan(plan);
        return result;
    }

    // ────────────────────────────────────────────────────────────────
    // Fast 模式
    // ────────────────────────────────────────────────────────────────

    private AutonomousResult executeFastMode(AgentInstance instance, String userGoal, AutonomousSpec config) {
        userInteraction.showProgress("⚡ Fast 模式，直接开始执行...\n");
        return executeReActLoop(instance, userGoal, config);
    }

    // ────────────────────────────────────────────────────────────────
    // ReAct 循环核心
    // ────────────────────────────────────────────────────────────────

    private AutonomousResult executeReActLoop(AgentInstance instance, String userGoal, AutonomousSpec config) {
        ChatModel model = instance.getModel();
        String agentName = instance.getName();
        int maxSteps = config.getMaxSteps();

        // 收集工具
        List<ToolSpecification> tools = instance.hasTools()
                ? new ArrayList<>(instance.getToolSpecifications())
                : new ArrayList<>();
        Map<String, ToolExecutor> toolExecutors = instance.hasTools()
                ? new HashMap<>(instance.getToolExecutors())
                : new HashMap<>();

        // 主动发现：auto_discover_mcp=true 且当前无工具时，基于任务目标先搜索并挂载 MCP
        int dynamicDiscoveryAttempts = 0;
        if (tools.isEmpty() && registry != null && instance.getSpec().isAutoDiscoverMcp()) {
            userInteraction.showProgress("🔍 正在从 MCP 仓库中自动发现可用工具...");
            log.info("[{}] 自主模式主动 MCP 发现, goal={}", agentName, truncate(userGoal, 60));
            boolean found = registry.tryAutoDiscoverAndAttachTool(instance, "", userGoal);
            if (found) {
                tools = new ArrayList<>(instance.getToolSpecifications());
                toolExecutors = new HashMap<>(instance.getToolExecutors());
                dynamicDiscoveryAttempts++;
                String toolNames = tools.stream()
                        .map(t -> t.name())
                        .collect(java.util.stream.Collectors.joining(", "));
                userInteraction.showProgress("✅ 已自动发现并挂载 " + tools.size() + " 个工具: " + toolNames + "\n");
                log.info("[{}] 自主模式主动 MCP 发现完成，加载了 {} 个工具", agentName, tools.size());
            } else {
                userInteraction.showProgress("⚠️  未能从 MCP 仓库找到匹配工具，将以无工具模式继续执行\n");
                log.warn("[{}] 自主模式主动 MCP 发现未找到合适工具，继续执行（无工具）", agentName);
            }
        }

        // 构建 ReAct System Prompt
        String reactSystemPrompt = buildReActSystemPrompt(
                instance.getSpec().getSystemPrompt(), userGoal, tools);

        // 消息历史
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(reactSystemPrompt));
        messages.add(UserMessage.from("请开始执行以下任务目标：\n" + userGoal));

        // 保存到记忆
        if (instance.getMemory() != null) {
            instance.getMemory().add(UserMessage.from(userGoal));
        }

        AutonomousResult result = new AutonomousResult();
        List<AutonomousResult.StepResult> stepResults = new ArrayList<>();
        int stepCount = 0;

        while (true) {
            stepCount++;

            // max_steps 检查
            if (stepCount > maxSteps) {
                String progressSummary = buildProgressSummary(stepResults);
                boolean shouldContinue = userInteraction.confirmContinue(stepCount - 1, maxSteps, progressSummary);

                if (shouldContinue) {
                    maxSteps += config.getMaxSteps(); // 额外增加一轮 max_steps
                    userInteraction.showProgress("▶️ 继续执行（新上限: " + maxSteps + " 步）\n");
                    log.info("[{}] 用户确认继续，新上限: {} 步", agentName, maxSteps);
                } else {
                    log.info("[{}] 用户选择终止，已执行 {} 步", agentName, stepCount - 1);
                    result.setCompleted(false);
                    result.setTerminationReason("用户终止（已执行 " + (stepCount - 1) + " 步）");
                    result.setFinalAnswer(buildPartialAnswer(stepResults));
                    break;
                }
            }

            userInteraction.showProgress(String.format("🔄 步骤 %d/%d...", stepCount, maxSteps));

            // 调用 LLM
            long stepStart = System.currentTimeMillis();
            ChatResponse response;
            try {
                ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(messages);
                if (!tools.isEmpty()) {
                    requestBuilder.toolSpecifications(tools);
                }
                response = model.chat(requestBuilder.build());
            } catch (Exception e) {
                log.error("[{}] 步骤 {} 模型调用失败: {}", agentName, stepCount, e.getMessage());
                stepResults.add(new AutonomousResult.StepResult(
                        stepCount, "模型调用", "失败: " + e.getMessage(),
                        System.currentTimeMillis() - stepStart));
                result.setCompleted(false);
                result.setTerminationReason("模型调用失败: " + e.getMessage());
                result.setFinalAnswer(buildPartialAnswer(stepResults));
                break;
            }

            AiMessage aiMessage = response.aiMessage();
            messages.add(aiMessage);

            // 保存到记忆
            if (instance.getMemory() != null) {
                instance.getMemory().add(aiMessage);
            }

            // 没有工具调用 → Agent 认为任务完成（或无更多操作）
            if (!aiMessage.hasToolExecutionRequests()) {
                String text = aiMessage.text();
                if (text == null || text.isBlank()) {
                    text = "✅ 任务已完成（模型无额外回复）";
                }
                long stepDuration = System.currentTimeMillis() - stepStart;
                stepResults.add(new AutonomousResult.StepResult(
                        stepCount, "最终回答", text, stepDuration));

                userInteraction.showProgress("✅ 任务完成\n");
                result.setCompleted(true);
                result.setFinalAnswer(text);
                break;
            }

            // 有工具调用 → 执行工具
            StringBuilder actionDesc = new StringBuilder();
            StringBuilder observationDesc = new StringBuilder();

            for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                String toolName = toolRequest.name();
                actionDesc.append(toolName).append("(").append(truncate(toolRequest.arguments(), 80)).append(") ");

                ToolExecutor executor = toolExecutors.get(toolName);
                String toolResult;

                // 被动发现：工具不存在时尝试动态挂载（每轮最多 1 次）
                if (executor == null && registry != null
                        && instance.getSpec().isAutoDiscoverMcp()
                        && dynamicDiscoveryAttempts < 1) {
                    dynamicDiscoveryAttempts++;
                    userInteraction.showProgress("🔍 工具 '" + toolName + "' 未找到，正在从 MCP 仓库自动发现...");
                    boolean found = registry.tryAutoDiscoverAndAttachTool(instance, toolName, userGoal);
                    if (found) {
                        tools = new ArrayList<>(instance.getToolSpecifications());
                        toolExecutors = new HashMap<>(instance.getToolExecutors());
                        executor = toolExecutors.get(toolName);
                        userInteraction.showProgress("✅ 工具 '" + toolName + "' 已自动挂载\n");
                        log.info("[{}] 被动 MCP 发现成功，工具 '{}' 已挂载", agentName, toolName);
                    } else {
                        userInteraction.showProgress("⚠️  未能从 MCP 仓库找到工具 '" + toolName + "'\n");
                        log.warn("[{}] 被动 MCP 发现失败，工具 '{}' 不可用", agentName, toolName);
                    }
                }

                if (executor == null) {
                    toolResult = "Error: Tool '" + toolName + "' not found";
                    log.warn("[{}] 未找到工具: {}", agentName, toolName);
                } else {
                    try {
                        toolResult = executor.execute(toolRequest, agentName);
                    } catch (Exception e) {
                        toolResult = "Error executing tool: " + e.getMessage();
                        log.error("[{}] 工具 '{}' 执行异常: {}", agentName, toolName, e.getMessage());
                    }
                }

                userInteraction.showProgress(String.format("   🔧 %s → %s",
                        toolName, truncate(toolResult, 120)));

                observationDesc.append("[").append(toolName).append("] ").append(truncate(toolResult, 200))
                        .append("\n");

                ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(toolRequest, toolResult);
                messages.add(resultMsg);
                if (instance.getMemory() != null) {
                    instance.getMemory().add(resultMsg);
                }
            }

            long stepDuration = System.currentTimeMillis() - stepStart;
            stepResults.add(new AutonomousResult.StepResult(
                    stepCount, actionDesc.toString().trim(),
                    observationDesc.toString().trim(), stepDuration));
        }

        result.setExecutedSteps(stepResults);
        result.setTotalSteps(stepResults.size());
        return result;
    }

    // ────────────────────────────────────────────────────────────────
    // Helper Methods
    // ────────────────────────────────────────────────────────────────

    private String buildReActSystemPrompt(String agentSystemPrompt, String userGoal,
            List<ToolSpecification> tools) {
        String toolDescriptions = tools.stream()
                .map(t -> "- " + t.name() + ": " + t.description())
                .collect(Collectors.joining("\n"));

        String basePrompt = agentSystemPrompt != null ? agentSystemPrompt + "\n\n" : "";

        return basePrompt + """
                ## 自主执行规则
                你正在自主执行模式下运行。你的目标是完成用户指定的任务。

                ### 执行流程
                1. 分析当前状态和目标
                2. 选择最合适的工具执行下一步操作
                3. 根据工具返回结果，判断是否需要继续
                4. 如果任务已完成，直接给出最终答案（不再调用工具）

                ### 注意事项
                - 每次只执行必要的操作
                - 如果遇到错误，尝试换个方法
                - 任务完成后，总结所有结果给出最终答案

                ### 可用工具
                %s
                """.formatted(toolDescriptions.isEmpty() ? "（无额外工具，仅使用对话能力）" : toolDescriptions);
    }

    private String buildProgressSummary(List<AutonomousResult.StepResult> steps) {
        if (steps.isEmpty())
            return "尚未执行任何步骤";
        StringBuilder sb = new StringBuilder();
        for (AutonomousResult.StepResult step : steps) {
            sb.append(String.format("  步骤 %d: %s\n", step.stepNumber(), truncate(step.action(), 60)));
        }
        return sb.toString();
    }

    private String buildPartialAnswer(List<AutonomousResult.StepResult> steps) {
        if (steps.isEmpty())
            return "未执行任何步骤。";
        AutonomousResult.StepResult lastStep = steps.get(steps.size() - 1);
        return "执行至第 " + lastStep.stepNumber() + " 步后中断。\n最后操作: "
                + lastStep.action() + "\n结果: " + lastStep.observation();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null)
            return "null";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
