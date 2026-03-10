package com.agentdsl.runtime.autonomous;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规划器引擎。
 * 调用 LLM 生成结构化的执行计划。
 */
public class PlannerEngine {

    private static final Logger log = LoggerFactory.getLogger(PlannerEngine.class);

    private static final String PLANNER_SYSTEM_PROMPT = """
            你是一个任务规划助手。根据用户的目标和可用工具，生成一个清晰的执行计划。

            ## 输出格式要求
            请按以下格式输出执行计划（纯文本，不需要 JSON）：

            思路: <你的整体规划思路>

            步骤:
            1. <步骤描述> | 工具: <计划使用的工具名> | 输入: <预期输入描述>
            2. <步骤描述> | 工具: <计划使用的工具名> | 输入: <预期输入描述>
            ...

            ## 规则
            1. 每个步骤应该是一个原子操作
            2. 步骤之间要有清晰的依赖关系
            3. 只使用"可用工具"列表中的工具
            4. 步骤数量控制在合理范围内（通常 3-8 步）
            """;

    private final ChatModel model;

    public PlannerEngine(ChatModel model) {
        this.model = model;
    }

    /**
     * 根据用户目标和可用工具生成执行计划。
     */
    public ExecutionPlan generatePlan(String userGoal, List<ToolSpecification> tools, String agentSystemPrompt) {
        log.info("开始生成执行计划，目标: {}", userGoal);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(PLANNER_SYSTEM_PROMPT));

        // 构造用户消息
        String toolDescriptions = tools.stream()
                .map(t -> "- " + t.name() + ": " + t.description())
                .collect(Collectors.joining("\n"));

        String userMessage = """
                ## 用户目标
                %s

                ## 可用工具
                %s

                ## Agent 角色背景
                %s

                请生成执行计划。
                """.formatted(userGoal,
                toolDescriptions.isEmpty() ? "（无额外工具，仅使用对话能力）" : toolDescriptions,
                agentSystemPrompt != null ? agentSystemPrompt : "通用助手");

        messages.add(UserMessage.from(userMessage));

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(messages)
                .build());

        String planText = response.aiMessage().text();
        log.debug("LLM 规划结果: {}", planText);

        return parsePlan(userGoal, planText);
    }

    /**
     * 根据用户反馈修改计划。
     */
    public ExecutionPlan revisePlan(ExecutionPlan currentPlan, String userFeedback,
            List<ToolSpecification> tools, String agentSystemPrompt) {
        log.info("根据用户反馈修改计划: {}", userFeedback);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(PLANNER_SYSTEM_PROMPT));

        String toolDescriptions = tools.stream()
                .map(t -> "- " + t.name() + ": " + t.description())
                .collect(Collectors.joining("\n"));

        String userMessage = """
                ## 用户目标
                %s

                ## 当前执行计划
                %s

                ## 用户修改意见
                %s

                ## 可用工具
                %s

                请根据用户的修改意见调整执行计划。
                """.formatted(
                currentPlan.getGoal(),
                currentPlan.toDisplayString(),
                userFeedback,
                toolDescriptions.isEmpty() ? "（无额外工具）" : toolDescriptions);

        messages.add(UserMessage.from(userMessage));

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(messages)
                .build());

        String planText = response.aiMessage().text();
        return parsePlan(currentPlan.getGoal(), planText);
    }

    /**
     * 解析 LLM 生成的计划文本为 ExecutionPlan 对象。
     */
    private ExecutionPlan parsePlan(String goal, String planText) {
        List<ExecutionPlan.PlanStep> steps = new ArrayList<>();
        String reasoning = null;

        String[] lines = planText.split("\n");
        int stepNumber = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            // 解析思路
            if (trimmed.startsWith("思路:") || trimmed.startsWith("思路：")) {
                reasoning = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                continue;
            }

            // 解析步骤行（如：1. 描述 | 工具: xxx | 输入: yyy）
            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                stepNumber++;
                String content = trimmed.replaceFirst("^\\d+\\.\\s+", "");

                String description = content;
                String toolName = null;
                String expectedInput = null;

                // 尝试解析 | 分隔的结构化信息
                if (content.contains("|")) {
                    String[] parts = content.split("\\|");
                    description = parts[0].trim();
                    for (int i = 1; i < parts.length; i++) {
                        String part = parts[i].trim();
                        if (part.startsWith("工具:") || part.startsWith("工具：")) {
                            toolName = part.substring(part.indexOf(':') + 1).trim();
                        } else if (part.startsWith("输入:") || part.startsWith("输入：")) {
                            expectedInput = part.substring(part.indexOf(':') + 1).trim();
                        }
                    }
                }

                steps.add(new ExecutionPlan.PlanStep(stepNumber, description, toolName, expectedInput));
            }
        }

        // 如果无法解析结构化步骤，将整个文本作为单步计划
        if (steps.isEmpty()) {
            steps.add(new ExecutionPlan.PlanStep(1, planText, null, null));
            reasoning = "LLM 返回了非结构化的计划文本";
        }

        return new ExecutionPlan(goal, steps, reasoning);
    }
}
