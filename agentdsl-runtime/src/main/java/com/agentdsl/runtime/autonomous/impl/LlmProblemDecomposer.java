package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import com.agentdsl.runtime.autonomous.pipeline.ProblemDecomposer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
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
 * LLM 驱动的问题解构器（Phase 1 核心实现）。
 * 通过一次 LLM 调用对用户目标进行结构化分析，输出 JSON，解析为 {@link ProblemSpec}。
 * JSON 解析失败时降级为 {@link DefaultProblemDecomposer}。
 */
public class LlmProblemDecomposer implements ProblemDecomposer {

    private static final Logger log = LoggerFactory.getLogger(LlmProblemDecomposer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT =
            "你是一个专业的任务分析师。请对用户目标进行结构化解构，只输出合法 JSON，不要有任何其他内容，不要 markdown 代码块。";

    private static final String PROMPT_TEMPLATE = """
            请对以下任务目标进行结构化分析：

            任务目标：%s
            可用工具：%s

            输出格式（JSON）：
            {
              "taskType": "single_step|multi_step|exploratory|open_ended",
              "complexity": "simple|medium|complex",
              "constraints": ["约束1"],
              "successCriteria": ["完成标准1", "完成标准2"],
              "uncertainties": ["不确定因素1"],
              "requiredTools": ["工具名"],
              "missingCapabilities": [],
              "estimatedSteps": 5,
              "decomposedSubGoals": [
                {"id": "sg1", "goal": "子目标1", "dependsOn": []},
                {"id": "sg2", "goal": "子目标2", "dependsOn": ["sg1"]}
              ]
            }
            """;

    private final ChatModel model;
    private final DefaultProblemDecomposer fallback = new DefaultProblemDecomposer();

    public LlmProblemDecomposer(ChatModel model) {
        this.model = model;
    }

    @Override
    public ProblemSpec decompose(String userGoal, List<ToolSpecification> availableTools) {
        String toolNames = availableTools.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.joining(", "));
        if (toolNames.isBlank()) toolNames = "无";

        String prompt = PROMPT_TEMPLATE.formatted(userGoal, toolNames);

        try {
            ChatResponse response = model.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(SYSTEM_PROMPT),
                            UserMessage.from(prompt)
                    ))
                    .build());
            String json = response.aiMessage().text();
            log.debug("Phase1 LLM 输出: {}", json);
            return parseJson(json, userGoal, availableTools);
        } catch (Exception e) {
            log.warn("Phase1 LLM 调用或解析失败，降级为 DefaultProblemDecomposer: {}", e.getMessage());
            return fallback.decompose(userGoal, availableTools);
        }
    }

    private ProblemSpec parseJson(String rawJson, String userGoal,
                                   List<ToolSpecification> availableTools) throws Exception {
        String json = extractJson(rawJson);
        JsonNode obj = MAPPER.readTree(json);

        ProblemSpec.TaskType taskType = parseTaskType(getString(obj, "taskType", "multi_step"));
        ProblemSpec.ComplexityLevel complexity = parseComplexity(getString(obj, "complexity", "medium"));
        int estimatedSteps = obj.has("estimatedSteps") ? obj.get("estimatedSteps").asInt(5) : 5;

        List<String> constraints = parseStringList(obj, "constraints");
        List<String> successCriteria = parseStringList(obj, "successCriteria");
        if (successCriteria.isEmpty()) {
            successCriteria = List.of("用户目标得到满足，任务完成");
        }
        List<String> uncertainties = parseStringList(obj, "uncertainties");
        List<String> requiredTools = parseStringList(obj, "requiredTools");
        List<String> missingCapabilities = parseStringList(obj, "missingCapabilities");

        // 解析子目标
        List<ProblemSpec.SubGoal> subGoals = new ArrayList<>();
        if (obj.has("decomposedSubGoals") && obj.get("decomposedSubGoals").isArray()) {
            for (JsonNode sg : obj.get("decomposedSubGoals")) {
                String id = getString(sg, "id", "sg" + (subGoals.size() + 1));
                String goal = getString(sg, "goal", "");
                List<String> deps = parseStringList(sg, "dependsOn");
                subGoals.add(new ProblemSpec.SubGoal(id, goal, deps));
            }
        }

        return ProblemSpec.builder(userGoal)
                .taskType(taskType)
                .complexity(complexity)
                .constraints(constraints)
                .successCriteria(successCriteria)
                .uncertainties(uncertainties)
                .requiredTools(requiredTools)
                .missingCapabilities(missingCapabilities)
                .estimatedSteps(estimatedSteps)
                .decomposedSubGoals(subGoals)
                .build();
    }

    // ── 工具方法 ─────────────────────────────────────────────────────

    private String extractJson(String text) {
        if (text == null) throw new IllegalArgumentException("LLM 返回为空");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = text.substring(start, end + 1);
            if (!isBalanced(json)) {
                throw new IllegalArgumentException("LLM 返回的 JSON 不完整（可能被截断）");
            }
            return json;
        }
        return text.trim();
    }

    private boolean isBalanced(String json) {
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        char prev = 0;
        for (char c : json.toCharArray()) {
            if (c == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
            }
            prev = c;
        }
        return braceCount == 0 && bracketCount == 0;
    }

    private String getString(JsonNode node, String key, String defaultValue) {
        return node.has(key) && !node.get(key).isNull()
                ? node.get(key).asText() : defaultValue;
    }

    private List<String> parseStringList(JsonNode node, String key) {
        if (!node.has(key) || !node.get(key).isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode el : node.get(key)) {
            if (!el.isNull()) result.add(el.asText());
        }
        return result;
    }

    private ProblemSpec.TaskType parseTaskType(String value) {
        return switch (value.toLowerCase()) {
            case "single_step" -> ProblemSpec.TaskType.SINGLE_STEP;
            case "exploratory" -> ProblemSpec.TaskType.EXPLORATORY;
            case "open_ended"  -> ProblemSpec.TaskType.OPEN_ENDED;
            default            -> ProblemSpec.TaskType.MULTI_STEP;
        };
    }

    private ProblemSpec.ComplexityLevel parseComplexity(String value) {
        return switch (value.toLowerCase()) {
            case "simple"  -> ProblemSpec.ComplexityLevel.SIMPLE;
            case "complex" -> ProblemSpec.ComplexityLevel.COMPLEX;
            default        -> ProblemSpec.ComplexityLevel.MEDIUM;
        };
    }
}
