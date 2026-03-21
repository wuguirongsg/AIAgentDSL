package com.agentdsl.runtime.autonomous.impl;

import com.agentdsl.runtime.autonomous.model.CandidateStrategy;
import com.agentdsl.runtime.autonomous.model.ExecutionStrategy;
import com.agentdsl.runtime.autonomous.model.ProblemSpec;
import com.agentdsl.runtime.autonomous.pipeline.StrategyPlanner;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 线性策略规划器（Phase 2 轻量实现）。
 * 单路径、文本格式计划，与现有 plan/fast 模式行为保持兼容。
 * 适用于 {@code plan} 和 {@code fast} preset。
 */
public class LinearStrategyPlanner implements StrategyPlanner {

    private static final Logger log = LoggerFactory.getLogger(LinearStrategyPlanner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是一个任务规划助手。根据用户目标和可用工具，生成一个清晰的执行计划。
            以 JSON 对象格式输出（不要 markdown），只包含一个策略：
            {
              "name": "策略名称",
              "approach": "核心思路",
              "steps": [{"action": "步骤描述", "tool": "工具名或null", "risk": "low|medium|high"}],
              "confidence": 0.75
            }
            """;

    private final ChatModel model;

    public LinearStrategyPlanner(ChatModel model) {
        this.model = model;
    }

    @Override
    public ExecutionStrategy plan(ProblemSpec problem, List<ToolSpecification> availableTools) {
        List<CandidateStrategy> candidates = generateSingleCandidate(problem, availableTools);
        CandidateStrategy candidate = candidates.get(0);
        ExecutionStrategy.ScoredStrategy scored =
                new ExecutionStrategy.ScoredStrategy(candidate, candidate.getConfidence(), Map.of());
        return new ExecutionStrategy(scored, null, List.of(scored));
    }

    /**
     * 生成单条候选策略（供 TotStrategyPlanner 降级时调用）。
     */
    List<CandidateStrategy> generateSingleCandidate(ProblemSpec problem,
                                                     List<ToolSpecification> tools) {
        String toolNames = tools.stream().map(ToolSpecification::name)
                .collect(Collectors.joining(", "));

        String userPrompt = """
                ## 任务目标
                %s

                ## 可用工具
                %s

                ## 成功标准
                %s

                请生成执行计划。
                """.formatted(
                problem.getOriginalGoal(),
                toolNames.isBlank() ? "（无工具）" : toolNames,
                String.join("; ", problem.getSuccessCriteria())
        );

        try {
            ChatResponse response = model.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(SYSTEM_PROMPT),
                            UserMessage.from(userPrompt)
                    )).build());

            String json = response.aiMessage().text();
            log.debug("Phase2 Linear LLM 输出: {}", json);
            return List.of(parseCandidate(json));
        } catch (Exception e) {
            log.warn("Phase2 Linear LLM 失败，返回空策略: {}", e.getMessage());
            return List.of(emptyCandidate());
        }
    }

    private CandidateStrategy parseCandidate(String rawJson) {
        try {
            String json = extractJson(rawJson);
            JsonNode obj = MAPPER.readTree(json);
            String name = getStr(obj, "name", "执行计划");
            String approach = getStr(obj, "approach", "");
            double confidence = obj.has("confidence") ? obj.get("confidence").asDouble() : 0.7;

            List<CandidateStrategy.Step> steps = new ArrayList<>();
            if (obj.has("steps") && obj.get("steps").isArray()) {
                for (JsonNode s : obj.get("steps")) {
                    String tool = (s.has("tool") && !s.get("tool").isNull())
                            ? s.get("tool").asText() : null;
                    steps.add(new CandidateStrategy.Step(
                            getStr(s, "action", ""), tool, getStr(s, "risk", "low")));
                }
            }
            return CandidateStrategy.builder("linear_1", name)
                    .approach(approach).steps(steps).confidence(confidence).build();
        } catch (Exception e) {
            log.warn("Linear plan JSON 解析失败: {}", e.getMessage());
            return emptyCandidate();
        }
    }

    private CandidateStrategy emptyCandidate() {
        return CandidateStrategy.builder("linear_default", "直接执行")
                .approach("基于目标直接进行 ReAct 推理")
                .steps(List.of()).confidence(0.5).build();
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = text.substring(start, end + 1);
            if (!isBalanced(json)) {
                throw new IllegalArgumentException("LLM 返回的 JSON 不完整（可能被截断）");
            }
            return json;
        }
        return "{}";
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

    private String getStr(JsonNode node, String key, String def) {
        return node.has(key) && !node.get(key).isNull() ? node.get(key).asText() : def;
    }
}
