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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ToT（思维树）策略规划器（Phase 2 高级实现）。
 * 一次 LLM 调用生成多条不同思路的候选路径，多维度评分，选出主路径 + 备用路径。
 * 适用于 {@code smart} preset（复杂/探索性任务）。
 */
public class TotStrategyPlanner implements StrategyPlanner {

    private static final Logger log = LoggerFactory.getLogger(TotStrategyPlanner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CANDIDATE_COUNT = 3;

    private static final String SYSTEM_PROMPT =
            "你是一个专业的任务规划师。请生成多种不同思路的执行策略，只输出合法 JSON 数组，不要有其他内容。";

    private static final String PROMPT_TEMPLATE = """
            基于以下任务分析，生成 %d 种不同思路的执行策略方案（每种策略代表不同路径，而不只是步骤数差异）。

            任务：%s
            任务类型：%s
            子目标：%s
            约束：%s
            成功标准：%s
            可用工具：%s

            输出 JSON 数组，每个元素：
            {
              "id": "strategy_1",
              "name": "策略名称",
              "approach": "核心思路一句话",
              "steps": [{"action": "步骤描述", "tool": "工具名或null", "risk": "low|medium|high"}],
              "tradeoffs": "优缺点",
              "estimatedSteps": 5,
              "confidence": 0.8
            }
            """;

    private final ChatModel model;

    public TotStrategyPlanner(ChatModel model) {
        this.model = model;
    }

    @Override
    public ExecutionStrategy plan(ProblemSpec problem, List<ToolSpecification> availableTools) {
        List<CandidateStrategy> candidates = generateCandidates(problem, availableTools);
        return selectBest(candidates, problem, availableTools);
    }

    // ── 步骤 2a：生成候选 ─────────────────────────────────────────────

    private List<CandidateStrategy> generateCandidates(ProblemSpec problem,
                                                        List<ToolSpecification> tools) {
        String toolNames = tools.stream().map(ToolSpecification::name)
                .collect(Collectors.joining(", "));
        String subGoals = problem.getDecomposedSubGoals().stream()
                .map(sg -> sg.goal()).collect(Collectors.joining("; "));
        if (subGoals.isBlank()) subGoals = "（未分解）";

        String prompt = PROMPT_TEMPLATE.formatted(
                CANDIDATE_COUNT,
                problem.getOriginalGoal(),
                problem.getTaskType(),
                subGoals,
                String.join("; ", problem.getConstraints()),
                String.join("; ", problem.getSuccessCriteria()),
                toolNames.isBlank() ? "无" : toolNames
        );

        try {
            ChatResponse response = model.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(SYSTEM_PROMPT),
                            UserMessage.from(prompt)
                    )).build());
            String json = response.aiMessage().text();
            log.debug("Phase2 ToT LLM 输出: {}", json);
            return parseCandidates(json);
        } catch (Exception e) {
            log.warn("Phase2 ToT LLM 失败，使用线性规划降级: {}", e.getMessage());
            return new LinearStrategyPlanner(model).generateSingleCandidate(problem, tools);
        }
    }

    // ── 步骤 2b：多维评分 ─────────────────────────────────────────────

    ExecutionStrategy.ScoredStrategy score(CandidateStrategy strategy,
                                            ProblemSpec problem,
                                            List<ToolSpecification> availableTools) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        double total = 0.0;

        // 1. 工具可用性（35%）
        long toolStepsTotal = strategy.getSteps().stream().filter(s -> s.tool() != null).count();
        long toolStepsAvail = strategy.getSteps().stream()
                .filter(s -> s.tool() != null)
                .filter(s -> availableTools.stream().anyMatch(t -> t.name().equals(s.tool())))
                .count();
        double toolScore = toolStepsTotal == 0 ? 1.0 : (double) toolStepsAvail / toolStepsTotal;
        breakdown.put("tool_availability", toolScore * 0.35);
        total += toolScore * 0.35;

        // 2. 步骤数适配（20%）
        int gap = Math.abs(strategy.getEstimatedSteps() - problem.getEstimatedSteps());
        double stepScore = 1.0 - Math.min(1.0, gap / 10.0);
        breakdown.put("step_fitness", stepScore * 0.20);
        total += stepScore * 0.20;

        // 3. 风险控制（25%）
        long highRisk = strategy.getSteps().stream().filter(s -> "high".equals(s.risk())).count();
        double riskScore = strategy.getSteps().isEmpty() ? 0.5
                : 1.0 - (double) highRisk / strategy.getSteps().size();
        breakdown.put("risk", riskScore * 0.25);
        total += riskScore * 0.25;

        // 4. LLM 自评置信度（20%）
        breakdown.put("llm_confidence", strategy.getConfidence() * 0.20);
        total += strategy.getConfidence() * 0.20;

        return new ExecutionStrategy.ScoredStrategy(strategy, total, breakdown);
    }

    // ── 步骤 2c：选最优 + 备用 ────────────────────────────────────────

    private ExecutionStrategy selectBest(List<CandidateStrategy> candidates,
                                          ProblemSpec problem,
                                          List<ToolSpecification> tools) {
        List<ExecutionStrategy.ScoredStrategy> scored = candidates.stream()
                .map(c -> score(c, problem, tools))
                .sorted(Comparator.comparingDouble(ExecutionStrategy.ScoredStrategy::score).reversed())
                .collect(Collectors.toList());

        ExecutionStrategy.ScoredStrategy best = scored.get(0);
        ExecutionStrategy.ScoredStrategy fallback = scored.size() > 1 ? scored.get(1) : null;

        log.info("Phase2 策略选择: 最优='{}' (score={}), 备用='{}'",
                best.strategy().getName(), String.format("%.2f", best.score()),
                fallback != null ? fallback.strategy().getName() : "无");

        return new ExecutionStrategy(best, fallback, scored);
    }

    // ── JSON 解析 ─────────────────────────────────────────────────────

    private List<CandidateStrategy> parseCandidates(String rawJson) throws Exception {
        String json = extractJsonArray(rawJson);
        JsonNode arr = MAPPER.readTree(json);
        List<CandidateStrategy> result = new ArrayList<>();

        for (JsonNode obj : arr) {
            String id = getStr(obj, "id", "s" + (result.size() + 1));
            String name = getStr(obj, "name", "策略" + (result.size() + 1));

            List<CandidateStrategy.Step> steps = new ArrayList<>();
            if (obj.has("steps") && obj.get("steps").isArray()) {
                for (JsonNode s : obj.get("steps")) {
                    String toolName = (s.has("tool") && !s.get("tool").isNull())
                            ? s.get("tool").asText() : null;
                    steps.add(new CandidateStrategy.Step(
                            getStr(s, "action", ""),
                            toolName,
                            getStr(s, "risk", "low")
                    ));
                }
            }

            result.add(CandidateStrategy.builder(id, name)
                    .approach(getStr(obj, "approach", ""))
                    .tradeoffs(getStr(obj, "tradeoffs", ""))
                    .steps(steps)
                    .estimatedSteps(obj.has("estimatedSteps")
                            ? obj.get("estimatedSteps").asInt() : steps.size())
                    .confidence(obj.has("confidence")
                            ? obj.get("confidence").asDouble() : 0.5)
                    .build());
        }
        return result.isEmpty() ? new LinearStrategyPlanner(model).generateSingleCandidate(
                ProblemSpec.defaultSpec("task"), List.of()) : result;
    }

    private String extractJsonArray(String text) {
        if (text == null) throw new IllegalArgumentException("LLM 返回为空");
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String json = text.substring(start, end + 1);
            if (!isBalanced(json)) {
                throw new IllegalArgumentException("LLM 返回的 JSON 不完整（可能被截断）");
            }
            return json;
        }
        return "[]";
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
