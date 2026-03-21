package com.agentdsl.runtime.autonomous;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.metrics.DebugEvent;
import com.agentdsl.core.metrics.DebugTracer;
import com.agentdsl.core.spec.AutonomousSpec;
import com.agentdsl.runtime.AgentInstance;
import com.agentdsl.runtime.AgentRegistry;
import com.agentdsl.runtime.LlmCallListener;
import com.agentdsl.runtime.autonomous.impl.PipelineFactory;
import com.agentdsl.runtime.autonomous.model.MonitorSignal;
import com.agentdsl.runtime.autonomous.model.StepContext;
import com.agentdsl.runtime.autonomous.pipeline.AutonomousPipeline;
import com.agentdsl.runtime.autonomous.pipeline.PipelineContext;
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

    /** 可选的 LLM 调用监听器，null 表示不打印（默认）。 */
    private LlmCallListener llmCallListener;

    /** 可选的 Pipeline 思考过程监听器，null 表示不输出（默认）。 */
    private com.agentdsl.runtime.autonomous.pipeline.PipelineThoughtListener pipelineThoughtListener;

    public AutonomousExecutor(UserInteraction userInteraction) {
        this(userInteraction, null);
    }

    public AutonomousExecutor(UserInteraction userInteraction, AgentRegistry registry) {
        this.userInteraction = userInteraction;
        this.registry = registry;
    }

    /**
     * 设置 LLM 调用监听器（例如 --verbose 模式的控制台打印器）。
     * 传入 null 可关闭监听。
     */
    public void setLlmCallListener(LlmCallListener llmCallListener) {
        this.llmCallListener = llmCallListener;
    }

    /**
     * 设置 Pipeline 思考过程监听器（例如 --think 模式的控制台打印器）。
     * 传入 null 可关闭监听。
     */
    public void setPipelineThoughtListener(
            com.agentdsl.runtime.autonomous.pipeline.PipelineThoughtListener listener) {
        this.pipelineThoughtListener = listener;
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

        log.info("[{}] 自主模式启动 (mode={}, preset={}, max_steps={}), 目标: {}",
                instance.getName(), config.getExecutionMode(),
                config.getPreset(), config.getMaxSteps(), userGoal);

        if (DebugTracer.isEnabled()) {
            Map<String, Object> details = new HashMap<>();
            details.put("mode", config.getExecutionMode());
            details.put("preset", config.getPreset());
            details.put("maxSteps", config.getMaxSteps());
            details.put("goal", userGoal);
            DebugTracer.record(DebugEvent.Type.AGENT_START, instance.getName(), details);
            DebugTracer.enter();
        }

        try {
            // ══ 构建 Pipeline（根据 preset 选择实现组合）══════════════
            ChatModel model = instance.getModel();
            AutonomousPipeline pipeline = PipelineFactory.create(config, model, pipelineThoughtListener);

            List<ToolSpecification> tools = instance.hasTools()
                    ? new ArrayList<>(instance.getToolSpecifications()) : List.of();

            // ══ Phase 1 + Phase 2：问题解构 + 策略规划 ════════════════
            String phaseLabel = switch (config.getPreset().toLowerCase()) {
                case "smart" -> "🔍 分析任务结构并生成多路径策略...";
                case "fast"  -> "⚡ Fast 模式，快速分析任务...";
                default      -> "🧠 分析任务并生成执行计划...";
            };
            userInteraction.showProgress(phaseLabel);
            PipelineContext pipelineCtx = pipeline.prepare(userGoal, tools);

            log.info("[{}] Pipeline 准备完成: {}", instance.getName(), pipelineCtx);

            // 缺少能力时提前告知
            if (pipelineCtx.getProblemSpec() != null
                    && !pipelineCtx.getProblemSpec().isExecutable()) {
                userInteraction.showProgress(
                    "⚠️  任务可能无法完全完成，缺少以下能力：\n" +
                    String.join("\n", pipelineCtx.getProblemSpec().getMissingCapabilities())
                );
            }

            if (config.isPlanMode()) {
                return executePlanMode(instance, userGoal, config, pipeline, pipelineCtx);
            } else {
                return executeFastMode(instance, userGoal, config, pipeline, pipelineCtx);
            }
        } finally {
            if (DebugTracer.isEnabled()) {
                DebugTracer.exit();
                DebugTracer.record(DebugEvent.Type.AGENT_END, instance.getName(),
                        Map.of("mode", config.getExecutionMode()));
            }
        }
    }

    /**
     * 交互式多轮对话模式。
     * 允许用户多次输入任务目标，保持上下文记忆。
     *
     * @param instance 已注册的 Agent 实例
     */
    public void executeInteractive(AgentInstance instance) {
        AutonomousSpec config = instance.getSpec().getAutonomous();
        if (config == null) {
            throw new DslRuntimeException("ADSL-030",
                    "Agent '" + instance.getName() + "' 未配置 autonomous 模式");
        }

        userInteraction.showWelcome(instance.getName());

        while (true) {
            String goal = userInteraction.readGoal();

            if (userInteraction.isExitCommand(goal)) {
                userInteraction.showGoodbye();
                break;
            }

            if (userInteraction.isHelpCommand(goal)) {
                userInteraction.showHelp();
                continue;
            }

            String trimmedGoal = goal.trim();
            if (trimmedGoal.isEmpty()) {
                continue;
            }

            try {
                AutonomousResult result = execute(instance, trimmedGoal);
                userInteraction.showResult(result);
            } catch (Exception e) {
                System.out.println("❌ 执行失败: " + e.getMessage());
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Plan 模式
    // ────────────────────────────────────────────────────────────────

    private AutonomousResult executePlanMode(AgentInstance instance, String userGoal,
            AutonomousSpec config, AutonomousPipeline pipeline, PipelineContext pipelineCtx) {
        ChatModel model = instance.getModel();
        List<ToolSpecification> tools = instance.hasTools()
                ? new ArrayList<>(instance.getToolSpecifications()) : List.of();

        // Plan 模式：仍然支持用户确认计划（基于 Phase 2 的策略）
        PlannerEngine planner = new PlannerEngine(model);
        userInteraction.showProgress("📋 生成执行计划（供确认）...");
        ExecutionPlan plan = planner.generatePlan(userGoal, tools, instance.getSpec().getSystemPrompt());
        log.info("[{}] 生成了 {} 步执行计划", instance.getName(), plan.getSteps().size());

        int maxPlanRevisions = 3;
        for (int revision = 0; revision < maxPlanRevisions; revision++) {
            PlanFeedback feedback = userInteraction.confirmPlan(plan.toDisplayString());

            if (feedback.isApproved()) {
                userInteraction.showProgress("✅ 计划已确认，开始执行...\n");
                break;
            } else if (feedback.isModify()) {
                userInteraction.showProgress("🔄 根据您的建议重新规划...");
                plan = planner.revisePlan(plan, feedback.userInput(), tools, instance.getSpec().getSystemPrompt());
                log.info("[{}] 计划已修改 (第 {} 次)", instance.getName(), revision + 1);
            } else {
                log.info("[{}] 用户拒绝执行计划", instance.getName());
                AutonomousResult result = new AutonomousResult();
                result.setPlan(plan);
                result.setCompleted(false);
                result.setTerminationReason("用户取消执行");
                result.setFinalAnswer("执行已取消。");
                return result;
            }
        }

        AutonomousResult result = executeReActLoop(instance, userGoal, config, plan, pipeline, pipelineCtx);
        result.setPlan(plan);
        return result;
    }

    // ────────────────────────────────────────────────────────────────
    // Fast 模式
    // ────────────────────────────────────────────────────────────────

    private AutonomousResult executeFastMode(AgentInstance instance, String userGoal,
            AutonomousSpec config, AutonomousPipeline pipeline, PipelineContext pipelineCtx) {
        userInteraction.showProgress("⚡ 开始执行...\n");
        return executeReActLoop(instance, userGoal, config, null, pipeline, pipelineCtx);
    }

    // ────────────────────────────────────────────────────────────────
    // ReAct 循环核心
    // ────────────────────────────────────────────────────────────────

    private AutonomousResult executeReActLoop(AgentInstance instance, String userGoal,
            AutonomousSpec config, ExecutionPlan plan,
            AutonomousPipeline pipeline, PipelineContext pipelineCtx) {
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

        // 构建 ReAct System Prompt（注入 Phase 1/2 上下文）
        String reactSystemPrompt = buildReActSystemPrompt(
                instance.getSpec().getSystemPrompt(), userGoal, tools, plan, pipelineCtx);

        // 消息历史：SystemMessage 必须放在最前，再追加 memory 历史（过滤掉旧 SystemMessage）
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(reactSystemPrompt));

        if (instance.getMemory() != null) {
            for (ChatMessage msg : instance.getMemory().messages()) {
                if (!(msg instanceof SystemMessage)) {
                    messages.add(msg);
                }
            }
        }

        messages.add(UserMessage.from("请开始执行以下任务目标：\n" + userGoal));

        // 保存到记忆
        if (instance.getMemory() != null) {
            instance.getMemory().add(UserMessage.from(userGoal));
        }

        AutonomousResult result = new AutonomousResult();
        List<AutonomousResult.StepResult> stepResults = new ArrayList<>();
        int stepCount = 0;

        int noToolCallCount = 0;
        int messageCompressionThreshold = Math.max(6, maxSteps / 2);

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
                // ✅ verbose: 打印发送内容
                if (llmCallListener != null) {
                    llmCallListener.onRequest(messages, tools);
                }
                response = model.chat(requestBuilder.build());
                // ✅ verbose: 打印返回内容
                if (llmCallListener != null) {
                    llmCallListener.onResponse(response.aiMessage(), System.currentTimeMillis() - stepStart);
                }
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

            // 保存到记忆
            if (instance.getMemory() != null) {
                instance.getMemory().add(aiMessage);
            }

            // 没有工具调用 → 检查是否包含 TASK_COMPLETE 标记或普通文本回复
            if (!aiMessage.hasToolExecutionRequests()) {
                messages.add(aiMessage);
                noToolCallCount++;
                String text = aiMessage.text();

                // 路径1：有 TASK_COMPLETE → 正常完成
                if (text != null && text.contains("TASK_COMPLETE:")) {
                    String finalAnswer = text.substring(
                        text.indexOf("TASK_COMPLETE:") + "TASK_COMPLETE:".length()).trim();
                    if (finalAnswer.isEmpty()) {
                        finalAnswer = "任务已完成";
                    }
                    long stepDuration = System.currentTimeMillis() - stepStart;
                    stepResults.add(new AutonomousResult.StepResult(
                            stepCount, "任务完成", finalAnswer, stepDuration));

                    userInteraction.showProgress("✅ 任务完成\n");
                    result.setCompleted(true);
                    result.setFinalAnswer(finalAnswer);
                    break;
                }

                // 路径2：无 TASK_COMPLETE，未达阈值 → 注入提示，继续循环
                if (noToolCallCount < 3) {
                    messages.add(UserMessage.from(
                        "[系统提示] 如果任务已完成，请输出 \"TASK_COMPLETE: [最终答案]\"。\n" +
                        "如果任务尚未完成，请继续使用工具推进，不要只输出文字。"
                    ));
                    continue;
                }

                // 路径3：连续 3 次无工具无 TASK_COMPLETE → 强制收尾
                log.warn("[{}] 连续 {} 步无工具调用且无 TASK_COMPLETE，强制结束", 
                         agentName, noToolCallCount);
                if (text == null || text.isBlank()) {
                    text = "任务执行完毕（无明确完成信号）";
                }
                long stepDuration = System.currentTimeMillis() - stepStart;
                stepResults.add(new AutonomousResult.StepResult(
                        stepCount, "强制结束", text, stepDuration));
                result.setCompleted(true);
                result.setFinalAnswer(text);
                break;
            }

            // 有工具调用 → 执行工具
            StringBuilder actionDesc = new StringBuilder();
            StringBuilder observationDesc = new StringBuilder();
            messages.add(aiMessage);
            noToolCallCount = 0;

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
                    toolResult = executeWithClassifiedError(executor, toolRequest, agentName);
                }

                userInteraction.showProgress(String.format("   🔧 %s → %s",
                        toolName, truncate(toolResult, 120)));

                observationDesc.append("[").append(toolName).append("] ").append(truncate(toolResult, 200))
                        .append("\n");

                ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(toolRequest, toolResult);
                messages.add(resultMsg);

                // 工具结果也必须保存到记忆，保证下轮对话的消息序列合法
                if (instance.getMemory() != null) {
                    instance.getMemory().add(resultMsg);
                }
            }

            long stepDuration = System.currentTimeMillis() - stepStart;
            stepResults.add(new AutonomousResult.StepResult(
                    stepCount, actionDesc.toString().trim(),
                    observationDesc.toString().trim(), stepDuration));

            // ══ Phase 4：元认知监控（每步结束后调用）══════════════════════
            List<StepContext.ToolCall> toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(r -> new StepContext.ToolCall(
                            r.name(),
                            r.arguments() != null ? r.arguments().hashCode() : 0,
                            observationDesc.toString().trim()))
                    .collect(Collectors.toList());

            StepContext stepCtx = new StepContext(
                    aiMessage.text(), toolCalls, 0 /* token 估算值，可接入实际计数 */);
            MonitorSignal signal = pipeline.getMonitor().analyze(stepCtx);

            if (pipeline.getThoughtListener() != null) {
                pipeline.getThoughtListener().onMonitorAnalysis(stepCount, stepCtx, signal);
            }

            if (signal.requiresIntervention()) {
                String injectionMsg = signal.buildInjectionMessage();
                if (signal.highestSeverity() == MonitorSignal.Severity.HIGH
                        && signal.hasType(MonitorSignal.InterventionType.STRATEGY_SWITCH)) {
                    // 高严重度停滞：切换备用策略
                    pipelineCtx.getExecutionStrategy().getFallbackStrategy().ifPresentOrElse(
                        fallback -> {
                            userInteraction.showProgress("🔄 主策略受阻，切换备用策略：" + fallback.getName());
                            messages.add(UserMessage.from(
                                "[策略切换] 当前路径受阻，切换到备用方案：\n" +
                                fallback.getApproach() + "\n\n" + injectionMsg));
                            pipelineCtx.activateFallbackStrategy();
                        },
                        () -> messages.add(UserMessage.from("[系统监控]\n" + injectionMsg))
                    );
                } else {
                    // 其他干预：注入引导消息，让 LLM 在下一轮 Thought 感知到
                    messages.add(UserMessage.from("[系统监控]\n" + injectionMsg));
                    if (signal.hasType(MonitorSignal.InterventionType.FORCE_CONCLUDE)) {
                        // 强制收尾：注入后立即让 LLM 给出最终答案
                        log.info("[{}] 元认知监控触发强制收尾", agentName);
                    }
                }
            }

            if (stepCount > messageCompressionThreshold) {
                List<ChatMessage> compressedMessages = new ArrayList<>();
                compressedMessages.add(SystemMessage.from(reactSystemPrompt));
                compressedMessages.add(UserMessage.from("请开始执行以下任务目标：\n" + userGoal));
                
                List<List<ChatMessage>> rounds = groupMessagesByRound(messages);
                int totalRounds = rounds.size();
                int fullDetailStart = Math.max(0, totalRounds - 5);
                
                if (fullDetailStart > 0) {
                    StringBuilder historySummary = new StringBuilder("【历史执行摘要】\n");
                    for (int i = 0; i < fullDetailStart; i++) {
                        historySummary.append(summarizeRound(i + 1, rounds.get(i))).append("\n");
                    }
                    compressedMessages.add(UserMessage.from(historySummary.toString()));
                    compressedMessages.add(AiMessage.from("已了解历史执行情况，继续执行任务。"));
                }
                
                for (int i = fullDetailStart; i < totalRounds; i++) {
                    compressedMessages.addAll(rounds.get(i));
                }
                
                messages.clear();
                messages.addAll(compressedMessages);
                messageCompressionThreshold += 5;
                log.info("[{}] 消息历史已压缩，当前 {} 轮", agentName, totalRounds);
            }
        }

        result.setExecutedSteps(stepResults);
        result.setTotalSteps(stepResults.size());
        return result;
    }

    // ────────────────────────────────────────────────────────────────
    // Helper Methods
    // ────────────────────────────────────────────────────────────────

    /** 不含 PipelineContext 的基础 Prompt（向后兼容用）。 */
    private String buildReActSystemPrompt(String agentSystemPrompt, String userGoal,
            List<ToolSpecification> tools) {
        String toolDescriptions = tools.stream()
                .map(t -> "- " + t.name() + ": " + t.description())
                .collect(Collectors.joining("\n"));

        String basePrompt = agentSystemPrompt != null ? agentSystemPrompt + "\n\n" : "";

        return basePrompt + """
                ## 自主执行模式
                你的目标：%s

                ### 思考格式（每步必须遵循）
                **Thought**: 分析当前状态，评估上一步结果，决定下一步行动
                **Reflect**: 当前进展是否符合预期？是否需要调整策略？
                **Action**: 选择工具或给出最终答案

                ### 任务完成标准
                当你认为任务完成时，不要调用任何工具，直接输出：
                "TASK_COMPLETE: [最终答案]"

                ### 遇到错误时
                - 工具调用失败 → 分析原因，考虑换一种方式
                - 信息不足 → 明确说明缺少什么，尝试其他途径获取
                - 策略无效 → 退回上一步，重新规划

                ### 可用工具
                %s
                """.formatted(userGoal,
                      toolDescriptions.isEmpty() ? "（无工具，仅使用推理能力）" : toolDescriptions);
    }

    /** 含 ExecutionPlan 的 Prompt（plan 模式向后兼容）。 */
    private String buildReActSystemPrompt(String agentSystemPrompt, String userGoal,
            List<ToolSpecification> tools, ExecutionPlan plan) {
        String base = buildReActSystemPrompt(agentSystemPrompt, userGoal, tools);
        if (plan == null) return base;
        return base + """

                ### 预定执行计划
                """ + plan.toDisplayString() + """
                请按照此计划执行，如需偏离请在 Reflect 中说明原因。
                """;
    }

    /** 含 PipelineContext 的完整 Prompt（注入成功标准 + 策略步骤）。 */
    private String buildReActSystemPrompt(String agentSystemPrompt, String userGoal,
            List<ToolSpecification> tools, ExecutionPlan plan, PipelineContext pipelineCtx) {
        String base = buildReActSystemPrompt(agentSystemPrompt, userGoal, tools, plan);
        if (pipelineCtx == null) return base;

        StringBuilder extra = new StringBuilder();

        // 注入成功标准（Phase 1 输出），让 LLM 知道明确的完成条件
        if (!pipelineCtx.getSuccessCriteria().isEmpty()) {
            extra.append("\n### 成功标准（满足以下任一条件即可输出 TASK_COMPLETE）\n");
            pipelineCtx.getSuccessCriteria().forEach(c -> extra.append("- ").append(c).append("\n"));
        }

        // 注入策略步骤（Phase 2 输出）
        String strategyText = pipelineCtx.getActiveStrategyStepsText();
        if (!strategyText.isBlank()) {
            extra.append("\n### 建议执行路径（Phase 2 规划）\n").append(strategyText).append("\n");
        }

        return base + extra;
    }

    private String buildProgressSummary(List<AutonomousResult.StepResult> steps) {
        if (steps.isEmpty())
            return "尚未执行任何步骤";
        StringBuilder sb = new StringBuilder();
        for (AutonomousResult.StepResult step : steps) {
            sb.append(String.format("  步骤 %d: %s → %s\n", 
                step.stepNumber(), 
                truncate(step.action(), 60),
                truncate(step.observation(), 100)));
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

    private List<List<ChatMessage>> groupMessagesByRound(List<ChatMessage> messages) {
        List<List<ChatMessage>> rounds = new ArrayList<>();
        List<ChatMessage> current = null;
        
        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage) {
                continue;
            }
            
            if (msg instanceof UserMessage um) {
                String text = um.singleText();
                if (text != null && text.startsWith("请开始执行以下任务目标：")) {
                    continue;
                }
                if (text != null && text.startsWith("[系统提示]") && current != null) {
                    current.add(msg);
                }
                continue;
            }
            
            if (msg instanceof AiMessage) {
                if (current != null) {
                    rounds.add(current);
                }
                current = new ArrayList<>();
                current.add(msg);
            } else if (msg instanceof ToolExecutionResultMessage && current != null) {
                current.add(msg);
            }
        }
        if (current != null) {
            rounds.add(current);
        }
        return rounds;
    }

    private String summarizeRound(int roundNum, List<ChatMessage> round) {
        StringBuilder sb = new StringBuilder();
        sb.append("[第").append(roundNum).append("轮摘要] ");
        
        List<String> toolSummaries = new ArrayList<>();
        for (ChatMessage msg : round) {
            if (msg instanceof ToolExecutionResultMessage r) {
                String toolName = r.toolName();
                String result = truncate(r.text(), 100);
                toolSummaries.add(toolName + "→" + result);
            }
        }
        
        sb.append(String.join(" | ", toolSummaries));
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────
    // 失败分类器
    // ────────────────────────────────────────────────────────────────

    private enum FailureType {
        NETWORK,
        PERMISSION,
        NOT_FOUND,
        TIMEOUT,
        UNKNOWN
    }

    private String executeWithClassifiedError(ToolExecutor executor,
                                               ToolExecutionRequest request,
                                               String agentName) {
        try {
            return executor.execute(request, agentName);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            FailureType type = classifyFailure(e, errorMsg);

            return switch (type) {
                case NETWORK -> String.format(
                    "[工具失败-网络] %s\n建议：检查网络连接或稍后重试，也可尝试其他数据来源。",
                    errorMsg);
                case PERMISSION -> String.format(
                    "[工具失败-权限] %s\n建议：此路径无访问权限，请改用 /tmp 目录或其他可写路径。",
                    errorMsg);
                case NOT_FOUND -> String.format(
                    "[工具失败-资源不存在] %s\n建议：目标资源不存在，请先确认资源位置或创建它。",
                    errorMsg);
                case TIMEOUT -> String.format(
                    "[工具失败-超时] %s\n建议：操作超时，可以缩小范围后重试。",
                    errorMsg);
                case UNKNOWN -> String.format(
                    "[工具失败-未知] %s\n建议：尝试换一种方式完成此步骤。",
                    errorMsg);
            };
        }
    }

    private FailureType classifyFailure(Exception e, String message) {
        if (e instanceof java.net.ConnectException 
            || e instanceof java.net.SocketTimeoutException
            || message.contains("connection refused")
            || message.contains("network")) {
            return FailureType.NETWORK;
        }
        if (e instanceof SecurityException 
            || message.contains("permission denied")
            || message.contains("access denied")) {
            return FailureType.PERMISSION;
        }
        if (e instanceof java.io.FileNotFoundException
            || message.contains("not found")
            || message.contains("no such file")) {
            return FailureType.NOT_FOUND;
        }
        if (e instanceof java.util.concurrent.TimeoutException
            || message.contains("timeout")
            || message.contains("timed out")) {
            return FailureType.TIMEOUT;
        }
        return FailureType.UNKNOWN;
    }

}
