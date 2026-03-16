这个问题很关键，直接说结论：**这三个阶段不是简单地在现有代码前后各插一段逻辑，它们之间有数据流依赖关系，需要重新设计整体的执行管道。**

先把四个阶段的依赖关系说清楚：

```
Phase 1（问题解构）
    ↓ 输出 ProblemSpec（问题类型、约束、成功标准）
Phase 2（策略规划）
    ↓ 输出 StrategyPlan（候选路径 + 评分）
Phase 3（ReAct 执行）
    ↑↓ 双向通信
Phase 4（元认知监控）
```

Phase 4 不是在最后跑的，它是包裹在 Phase 3 外面持续运行的监控层。现在逐一设计。

---

## Phase 1：问题接收与解构

### 当前问题

现有代码里，用户目标直接变成 `"请开始执行以下任务目标：\n" + userGoal` 塞进消息，没有任何前置分析。LLM 拿到的是原始自然语言，它自己理解、自己规划、自己执行——职责全混在一起。

### 设计：ProblemAnalyzer

```java
/**
 * 阶段1：问题接收与解构器。
 * 在任何规划和执行开始之前，先对用户目标做结构化分析。
 */
public class ProblemAnalyzer {

    private final ChatModel model;

    public ProblemAnalyzer(ChatModel model) {
        this.model = model;
    }

    public ProblemSpec analyze(String userGoal, List<ToolSpecification> availableTools) {
        String prompt = buildAnalysisPrompt(userGoal, availableTools);
        
        ChatResponse response = model.chat(ChatRequest.builder()
            .messages(List.of(
                SystemMessage.from("你是一个专业的任务分析师，负责对用户目标进行结构化解构。只输出JSON，不要有任何其他内容。"),
                UserMessage.from(prompt)
            ))
            .build());

        return parseAnalysisResult(response.aiMessage().text(), userGoal);
    }

    private String buildAnalysisPrompt(String userGoal, List<ToolSpecification> tools) {
        String toolNames = tools.stream()
            .map(ToolSpecification::name)
            .collect(Collectors.joining(", "));

        return """
            请对以下任务目标进行结构化分析，以JSON格式输出：
            
            任务目标：%s
            可用工具：%s
            
            输出格式：
            {
              "taskType": "single_step|multi_step|exploratory|open_ended",
              "complexity": "simple|medium|complex",
              "constraints": ["约束条件1", "约束条件2"],
              "successCriteria": ["成功标准1", "成功标准2"],
              "uncertainties": ["不确定因素1", "不确定因素2"],
              "requiredTools": ["需要的工具名"],
              "missingCapabilities": ["缺少的能力，如果有的话"],
              "estimatedSteps": 5,
              "riskLevel": "low|medium|high",
              "decomposedSubGoals": [
                {"id": "sg1", "goal": "子目标1", "dependsOn": []},
                {"id": "sg2", "goal": "子目标2", "dependsOn": ["sg1"]}
              ]
            }
            """.formatted(userGoal, toolNames.isEmpty() ? "无" : toolNames);
    }

    private ProblemSpec parseAnalysisResult(String json, String originalGoal) {
        try {
            // 解析 JSON，构建 ProblemSpec
            // 实际实现用 Gson 或 Jackson
            return ProblemSpec.fromJson(json, originalGoal);
        } catch (Exception e) {
            // 解析失败时降级：返回最简单的默认分析
            log.warn("问题解构失败，使用默认分析: {}", e.getMessage());
            return ProblemSpec.defaultSpec(originalGoal);
        }
    }
}
```

### ProblemSpec 数据模型

```java
public class ProblemSpec {
    private final String originalGoal;
    private final TaskType taskType;       // SINGLE_STEP, MULTI_STEP, EXPLORATORY
    private final ComplexityLevel complexity;
    private final List<String> constraints;
    private final List<String> successCriteria;  // 关键：知道什么叫"完成"
    private final List<String> uncertainties;
    private final List<String> requiredTools;
    private final List<String> missingCapabilities;
    private final int estimatedSteps;
    private final RiskLevel riskLevel;
    private final List<SubGoal> decomposedSubGoals;  // 子目标依赖图

    // 核心方法：这个问题能否用当前工具集解决？
    public boolean isExecutable() {
        return missingCapabilities.isEmpty();
    }

    // 根据任务类型推荐执行策略
    public RecommendedStrategy recommendStrategy() {
        return switch (taskType) {
            case SINGLE_STEP  -> RecommendedStrategy.DIRECT_REACT;
            case MULTI_STEP   -> RecommendedStrategy.PLAN_THEN_REACT;
            case EXPLORATORY  -> RecommendedStrategy.TOT_EXPLORATION;
            case OPEN_ENDED   -> RecommendedStrategy.ITERATIVE_REFINEMENT;
        };
    }
}
```

**Phase 1 的关键价值：`successCriteria`**。有了明确的成功标准，Phase 3 的 ReAct 循环就能在每步 Reflect 时问"是否满足了成功标准"，而不是靠 LLM 自己猜"我完成了吗"。

---

## Phase 2：策略规划

### 当前问题

现有 `PlannerEngine` 生成的是单一线性计划，本质上是"让 LLM 写一个步骤列表"。设计框架里要求的是 ToT（思维树）——生成多条候选路径、评分、剪枝、选最优路径。

### 设计：StrategyPlanner（升级现有 PlannerEngine）

分三个子步骤：

```java
/**
 * 阶段2：策略规划器。
 * 基于 ProblemSpec 生成多条候选策略，评分并选出最优执行路径。
 */
public class StrategyPlanner {

    private static final int CANDIDATE_COUNT = 3; // 生成候选策略数量
    private final ChatModel model;

    // ── 步骤 2a：生成候选策略（广度优先） ──────────────────────

    public List<CandidateStrategy> generateCandidates(ProblemSpec problem,
                                                        List<ToolSpecification> tools) {
        // 一次 LLM 调用生成 N 条候选路径
        String prompt = """
            基于以下任务分析，生成 %d 种不同的执行策略方案。
            
            任务：%s
            子目标：%s
            约束：%s
            成功标准：%s
            可用工具：%s
            
            每种策略应代表不同的思路和路径，而不只是步骤数量的差异。
            以JSON数组输出，每个策略包含：
            {
              "id": "strategy_1",
              "name": "策略名称",
              "approach": "核心思路描述",
              "steps": [{"action": "步骤描述", "tool": "工具名或null", "risk": "low|medium|high"}],
              "tradeoffs": "优缺点分析",
              "estimatedSteps": 5,
              "confidence": 0.8
            }
            """.formatted(
                CANDIDATE_COUNT,
                problem.getOriginalGoal(),
                formatSubGoals(problem.getDecomposedSubGoals()),
                String.join("; ", problem.getConstraints()),
                String.join("; ", problem.getSuccessCriteria()),
                tools.stream().map(ToolSpecification::name).collect(Collectors.joining(", "))
            );

        String json = callModel(prompt);
        return parseCandidates(json);
    }

    // ── 步骤 2b：对候选策略评分 ──────────────────────────────

    public ScoredStrategy score(CandidateStrategy strategy, 
                                 ProblemSpec problem,
                                 List<ToolSpecification> availableTools) {
        double score = 0.0;
        Map<String, Double> breakdown = new LinkedHashMap<>();

        // 1. 工具可用性评分（硬约束，最重要）
        long availableToolCount = strategy.getSteps().stream()
            .filter(step -> step.getTool() != null)
            .filter(step -> availableTools.stream()
                .anyMatch(t -> t.name().equals(step.getTool())))
            .count();
        long totalToolSteps = strategy.getSteps().stream()
            .filter(step -> step.getTool() != null).count();
        double toolScore = totalToolSteps == 0 ? 1.0 
            : (double) availableToolCount / totalToolSteps;
        breakdown.put("tool_availability", toolScore * 0.35);
        score += toolScore * 0.35;

        // 2. 步骤数量适配（太少可能不够，太多浪费）
        int estimated = problem.getEstimatedSteps();
        int actual = strategy.getEstimatedSteps();
        double stepScore = 1.0 - Math.min(1.0, Math.abs(actual - estimated) / 10.0);
        breakdown.put("step_fitness", stepScore * 0.20);
        score += stepScore * 0.20;

        // 3. 风险评分（高风险步骤占比）
        long highRiskSteps = strategy.getSteps().stream()
            .filter(s -> "high".equals(s.getRisk())).count();
        double riskScore = 1.0 - (double) highRiskSteps / strategy.getSteps().size();
        breakdown.put("risk", riskScore * 0.25);
        score += riskScore * 0.25;

        // 4. LLM 自评置信度
        breakdown.put("llm_confidence", strategy.getConfidence() * 0.20);
        score += strategy.getConfidence() * 0.20;

        return new ScoredStrategy(strategy, score, breakdown);
    }

    // ── 步骤 2c：选出最优策略并剪枝 ─────────────────────────

    public ExecutionStrategy selectBest(List<CandidateStrategy> candidates,
                                         ProblemSpec problem,
                                         List<ToolSpecification> tools) {
        List<ScoredStrategy> scored = candidates.stream()
            .map(c -> score(c, problem, tools))
            .sorted(Comparator.comparingDouble(ScoredStrategy::score).reversed())
            .collect(Collectors.toList());

        // 评分最高的策略作为主路径
        ScoredStrategy best = scored.get(0);

        // 评分第二的作为备用路径（用于回溯）
        ScoredStrategy fallback = scored.size() > 1 ? scored.get(1) : null;

        log.info("策略选择完成: 最优='{}' (score={:.2f}), 备用='{}'",
            best.getStrategy().getName(), best.score(),
            fallback != null ? fallback.getStrategy().getName() : "无");

        return new ExecutionStrategy(best, fallback, scored);
    }
}
```

### ExecutionStrategy 数据模型

```java
public class ExecutionStrategy {
    private final ScoredStrategy primary;       // 主执行路径
    private final ScoredStrategy fallback;      // 备用路径（主路径失败时切换）
    private final List<ScoredStrategy> allCandidates;  // 全部候选（用于调试）
    
    // Phase 3 失败时，从这里取备用策略
    public Optional<CandidateStrategy> getFallbackStrategy() {
        return Optional.ofNullable(fallback).map(ScoredStrategy::getStrategy);
    }
}
```

**Phase 2 和现有 PlannerEngine 的关系**：`StrategyPlanner` 是 `PlannerEngine` 的升级版，可以逐步替换。先保留 `PlannerEngine` 的 `plan` 模式作为简单入口，`StrategyPlanner` 作为高级模式启用。

---

## Phase 4：元认知监控层

### 当前问题

这是最容易被误解的一个阶段。它不是一个"第四步"——它是包裹在整个执行过程外面、持续运行的监控系统。

### 设计：MetaCognitiveMonitor

```java
/**
 * 阶段4：元认知监控器。
 * 持续监控 ReAct 执行循环的健康状态，在检测到异常时注入干预信号。
 * 
 * 监控四个维度：
 * 1. 进度停滞检测
 * 2. 置信度下降检测
 * 3. 资源超限预警
 * 4. 矛盾检测
 */
public class MetaCognitiveMonitor {

    // ── 监控状态 ───────────────────────────────────────────────

    // 维度1：停滞检测（前一轮已在 Phase3 里提过，这里整合进监控层）
    private final Deque<String> actionHistory = new ArrayDeque<>();
    private int stagnantStepCount = 0;

    // 维度2：置信度追踪
    private final Deque<Double> confidenceHistory = new ArrayDeque<>();
    private static final int CONFIDENCE_WINDOW = 3;

    // 维度3：资源追踪
    private int totalTokensUsed = 0;
    private final long startTimeMs = System.currentTimeMillis();

    // 维度4：矛盾检测 - 保存关键断言
    private final List<String> keyAssertions = new ArrayList<>();

    // 配置
    private final int maxTokenBudget;
    private final long maxTimeMs;
    private final int stagnationThreshold = 3;
    private final double confidenceDeclineThreshold = 0.15; // 连续下降超过15%触发

    public MetaCognitiveMonitor(int maxTokenBudget, long maxTimeMs) {
        this.maxTokenBudget = maxTokenBudget;
        this.maxTimeMs = maxTimeMs;
    }

    // ── 核心方法：每步执行后调用 ──────────────────────────────

    /**
     * 分析当前执行状态，返回监控信号。
     * ReAct 循环在每步结束后调用此方法，根据返回的信号决定是否干预。
     */
    public MonitorSignal analyze(StepContext ctx) {
        List<MonitorSignal.Intervention> interventions = new ArrayList<>();

        // 维度1：停滞检测
        checkStagnation(ctx).ifPresent(interventions::add);

        // 维度2：置信度下降
        checkConfidenceDecline(ctx).ifPresent(interventions::add);

        // 维度3：资源超限预警
        checkResourceLimits(ctx).ifPresent(interventions::add);

        // 维度4：矛盾检测
        checkContradictions(ctx).ifPresent(interventions::add);

        // 更新状态
        updateState(ctx);

        if (interventions.isEmpty()) {
            return MonitorSignal.healthy();
        }

        // 按严重程度排序，取最高级干预
        interventions.sort(Comparator.comparing(i -> i.severity()).reversed());
        return MonitorSignal.intervene(interventions);
    }

    // ── 四个监控维度的实现 ─────────────────────────────────────

    private Optional<MonitorSignal.Intervention> checkStagnation(StepContext ctx) {
        String fingerprint = ctx.toolsCalled.stream()
            .map(t -> t.name + ":" + t.argsHash)
            .collect(Collectors.joining("|"));

        actionHistory.addLast(fingerprint);
        if (actionHistory.size() > stagnationThreshold) actionHistory.removeFirst();

        boolean isStagnant = actionHistory.size() == stagnationThreshold
            && actionHistory.stream().distinct().count() == 1;

        if (isStagnant) {
            stagnantStepCount++;
            return Optional.of(new MonitorSignal.Intervention(
                InterventionType.STRATEGY_SWITCH,
                Severity.HIGH,
                "检测到执行停滞（连续 " + stagnationThreshold + " 步相同操作）。" +
                "当前路径可能已无效，建议：\n" +
                "1. 换一种工具或方法达成相同目标\n" +
                "2. 重新分析当前子目标是否可达\n" +
                "3. 考虑跳过此步骤，先完成其他子目标"
            ));
        }
        return Optional.empty();
    }

    private Optional<MonitorSignal.Intervention> checkConfidenceDecline(StepContext ctx) {
        // 从 LLM 输出中提取置信度信号（基于关键词启发式方法）
        double inferredConfidence = inferConfidence(ctx.llmOutput);

        confidenceHistory.addLast(inferredConfidence);
        if (confidenceHistory.size() > CONFIDENCE_WINDOW) {
            confidenceHistory.removeFirst();
        }

        if (confidenceHistory.size() < CONFIDENCE_WINDOW) return Optional.empty();

        List<Double> scores = new ArrayList<>(confidenceHistory);
        boolean consistentDecline = true;
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i) >= scores.get(i - 1)) {
                consistentDecline = false;
                break;
            }
        }

        double totalDecline = scores.get(0) - scores.get(scores.size() - 1);

        if (consistentDecline && totalDecline > confidenceDeclineThreshold) {
            return Optional.of(new MonitorSignal.Intervention(
                InterventionType.REPLAN,
                Severity.MEDIUM,
                "检测到执行置信度持续下降（" + 
                String.format("%.0f%%", totalDecline * 100) + " over " + 
                CONFIDENCE_WINDOW + " steps）。建议重新评估当前策略是否正确。"
            ));
        }
        return Optional.empty();
    }

    private Optional<MonitorSignal.Intervention> checkResourceLimits(StepContext ctx) {
        totalTokensUsed += ctx.tokensUsedThisStep;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;

        // Token 超限预警（80% 时警告，95% 时强制压缩）
        if (totalTokensUsed > maxTokenBudget * 0.95) {
            return Optional.of(new MonitorSignal.Intervention(
                InterventionType.COMPRESS_CONTEXT,
                Severity.HIGH,
                "Token 用量已达 " + String.format("%.0f%%", 
                    (double) totalTokensUsed / maxTokenBudget * 100) + 
                "，需要立即压缩上下文历史。"
            ));
        }
        if (totalTokensUsed > maxTokenBudget * 0.80) {
            return Optional.of(new MonitorSignal.Intervention(
                InterventionType.COMPRESS_CONTEXT,
                Severity.LOW,
                "Token 用量预警（已用 80%），建议开始精简中间步骤的记录。"
            ));
        }

        // 时间超限预警
        if (maxTimeMs > 0 && elapsedMs > maxTimeMs * 0.90) {
            return Optional.of(new MonitorSignal.Intervention(
                InterventionType.FORCE_CONCLUDE,
                Severity.HIGH,
                "执行时间已达上限的 90%，需要尽快得出结论，不要继续探索新路径。"
            ));
        }
        return Optional.empty();
    }

    private Optional<MonitorSignal.Intervention> checkContradictions(StepContext ctx) {
        // 提取本步骤的关键断言（LLM 输出里的事实性陈述）
        List<String> newAssertions = extractAssertions(ctx.llmOutput);

        for (String newAssertion : newAssertions) {
            for (String oldAssertion : keyAssertions) {
                if (isContradiction(newAssertion, oldAssertion)) {
                    return Optional.of(new MonitorSignal.Intervention(
                        InterventionType.CONTRADICTION_RESOLUTION,
                        Severity.MEDIUM,
                        "检测到潜在矛盾：\n" +
                        "- 早前断言：\"" + oldAssertion + "\"\n" +
                        "- 当前断言：\"" + newAssertion + "\"\n" +
                        "请重新核实哪个是正确的，避免基于错误前提继续推理。"
                    ));
                }
            }
        }
        keyAssertions.addAll(newAssertions);
        // 防止 keyAssertions 无限增长
        if (keyAssertions.size() > 20) {
            keyAssertions.subList(0, 10).clear();
        }
        return Optional.empty();
    }

    // ── 置信度推断（启发式，不需要 LLM 单独调用）──────────────

    private double inferConfidence(String llmOutput) {
        if (llmOutput == null) return 0.5;
        String lower = llmOutput.toLowerCase();

        // 高置信度信号
        double score = 0.5;
        if (lower.contains("已完成") || lower.contains("成功") || 
            lower.contains("找到了") || lower.contains("task_complete")) score += 0.3;
        if (lower.contains("明确") || lower.contains("确认")) score += 0.1;

        // 低置信度信号
        if (lower.contains("不确定") || lower.contains("可能") || 
            lower.contains("也许")) score -= 0.15;
        if (lower.contains("失败") || lower.contains("错误") || 
            lower.contains("无法")) score -= 0.2;
        if (lower.contains("重试") || lower.contains("尝试其他")) score -= 0.1;

        return Math.max(0.0, Math.min(1.0, score));
    }

    // 极简矛盾检测（生产级需要语义相似度模型）
    private boolean isContradiction(String a, String b) {
        // 检测包含相反关键词的相似陈述，这里是启发式简化版
        return false; // 实际实现需要语义比较
    }

    private List<String> extractAssertions(String text) {
        if (text == null) return List.of();
        // 提取以"文件存在"、"X是Y"、"已发现"等模式开头的句子
        return Arrays.stream(text.split("[。\n]"))
            .filter(s -> s.length() > 10 && s.length() < 100)
            .filter(s -> s.contains("是") || s.contains("存在") || s.contains("已"))
            .limit(3)
            .collect(Collectors.toList());
    }

    private void updateState(StepContext ctx) {
        // 如果本步有进展（工具调用成功），重置停滞计数
        boolean hasProgress = ctx.toolsCalled.stream()
            .anyMatch(t -> !t.result.startsWith("Error") && !t.result.startsWith("[工具失败"));
        if (hasProgress) stagnantStepCount = 0;
    }
}
```

### MonitorSignal 及干预类型

```java
public class MonitorSignal {
    
    public enum InterventionType {
        STRATEGY_SWITCH,         // 切换到备用策略
        REPLAN,                  // 重新规划（回到 Phase 2）
        COMPRESS_CONTEXT,        // 压缩消息历史
        FORCE_CONCLUDE,          // 强制收尾
        CONTRADICTION_RESOLUTION // 解决矛盾
    }

    public enum Severity { LOW, MEDIUM, HIGH }

    // 干预信号的消息会被注入到 ReAct 循环的下一轮 Prompt 里
    public String buildInjectionMessage() {
        return interventions.stream()
            .map(i -> "[元认知监控-" + i.severity() + "] " + i.message())
            .collect(Collectors.joining("\n\n"));
    }
}
```

---

## 四个阶段的完整集成

把以上全部整合进 `AutonomousExecutor`：

```java
public AutonomousResult execute(AgentInstance instance, String userGoal) {

    // ══ Phase 1：问题解构 ══════════════════════════════════════
    userInteraction.showProgress("🔍 分析任务结构...");
    ProblemSpec problem = new ProblemAnalyzer(instance.getModel())
        .analyze(userGoal, instance.getToolSpecifications());

    // 检查可执行性：缺少关键能力时提前告知用户
    if (!problem.isExecutable()) {
        userInteraction.showWarning(
            "⚠️  任务可能无法完全完成，缺少以下能力：\n" +
            String.join("\n", problem.getMissingCapabilities())
        );
    }

    log.info("[{}] 问题解构完成: type={}, complexity={}, estimatedSteps={}",
        instance.getName(), problem.getTaskType(), 
        problem.getComplexity(), problem.getEstimatedSteps());

    // ══ Phase 2：策略规划 ══════════════════════════════════════
    AutonomousSpec config = instance.getSpec().getAutonomous();
    ExecutionStrategy strategy;

    if (config.isPlanMode()) {
        userInteraction.showProgress("🧠 生成执行策略...");
        StrategyPlanner planner = new StrategyPlanner(instance.getModel());
        List<CandidateStrategy> candidates = 
            planner.generateCandidates(problem, instance.getToolSpecifications());
        strategy = planner.selectBest(candidates, problem, instance.getToolSpecifications());
        
        // 展示给用户确认（复用现有的确认流程，但内容更丰富）
        PlanFeedback feedback = userInteraction.confirmStrategy(strategy);
        if (feedback.isRejected()) {
            return AutonomousResult.cancelled("用户取消执行");
        }
    } else {
        // fast 模式：跳过用户确认，直接选最优策略
        userInteraction.showProgress("⚡ Fast 模式，直接分析最优路径...");
        StrategyPlanner planner = new StrategyPlanner(instance.getModel());
        List<CandidateStrategy> candidates = 
            planner.generateCandidates(problem, instance.getToolSpecifications());
        strategy = planner.selectBest(candidates, problem, instance.getToolSpecifications());
    }

    // ══ Phase 4 初始化：元认知监控器 ══════════════════════════
    MetaCognitiveMonitor monitor = new MetaCognitiveMonitor(
        config.getMaxTokenBudget(),  // 需要在 AutonomousSpec 增加这两个字段
        config.getMaxTimeMs()
    );

    // ══ Phase 3：ReAct 执行（携带 Phase1/2 的上下文）══════════
    return executeReActLoop(instance, userGoal, config, problem, strategy, monitor);
}
```

ReAct 主循环里增加监控调用：

```java
// 在每步工具执行完成后
StepContext ctx = new StepContext(
    aiMessage.text(),
    toolCallResults,      // 包含工具名、参数hash、结果
    tokensThisStep
);

MonitorSignal signal = monitor.analyze(ctx);

if (signal.requiresIntervention()) {
    String injectionMsg = signal.buildInjectionMessage();
    
    if (signal.highestSeverity() == Severity.HIGH 
        && signal.hasType(InterventionType.STRATEGY_SWITCH)) {
        // 高严重度停滞：切换到备用策略
        strategy.getFallbackStrategy().ifPresent(fallback -> {
            userInteraction.showProgress("🔄 主策略受阻，切换备用策略：" + fallback.getName());
            // 重新注入备用策略的执行引导
            messages.add(UserMessage.from(
                "[策略切换] 当前路径受阻，切换到备用方案：\n" + 
                fallback.getApproach() + "\n\n" + injectionMsg
            ));
        });
    } else {
        // 其他干预：注入引导消息，让 LLM 在下一轮 Thought 里感知到
        messages.add(UserMessage.from("[系统监控]\n" + injectionMsg));
    }
}
```

---

## 整体数据流

把四个阶段串起来看：

```
用户输入 userGoal
    │
    ▼ Phase 1
ProblemSpec
    • taskType → 决定用哪种执行策略
    • successCriteria → 注入 ReAct，用于判断何时真正完成
    • estimatedSteps → 给 max_steps 一个合理的初始值
    │
    ▼ Phase 2
ExecutionStrategy
    • primary.steps → 注入 ReAct System Prompt 作为计划指引
    • fallback → 停滞时切换路径
    │
    ▼ Phase 3 ←──────────────────────────────────────────────┐
ReAct 循环                                                    │
    每步结束 → StepContext                                    │
    │                                                         │
    ▼ Phase 4                                                 │
MetaCognitiveMonitor.analyze()                                │
    • HEALTHY → 继续                                          │
    • STRATEGY_SWITCH → 切换备用策略 ─────────────────────────┘
    • REPLAN → 回到 Phase 2 重新规划
    • COMPRESS_CONTEXT → 压缩历史消息
    • FORCE_CONCLUDE → 强制 LLM 给出当前最佳答案
    │
    ▼
AutonomousResult（含完整执行轨迹）
```

---

## 实施建议

说实话，把四个阶段全部完整实现，工作量相当大，而且不是所有任务都需要全部四个阶段。建议按这个策略推进：

| 阶段 | 核心价值 | 实施成本 | 建议时机 |
|---|---|---|---|
| Phase 1 问题解构 | `successCriteria` 让终止判断有依据 | 低（一次 LLM 调用） | **现在就做** |
| Phase 4 停滞检测 | 防止原地打转 | 低（无需 LLM） | **现在就做** |
| Phase 4 资源监控 | 防止 token 超限 | 低 | **现在就做** |
| Phase 2 策略规划 | 多路径备选，失败可回溯 | 中（多次 LLM 调用） | v1.5 |
| Phase 4 置信度跟踪 | 提前感知策略失效 | 中 | v1.5 |
| Phase 4 矛盾检测 | 防止错误前提累积 | 高（需语义模型） | v2.0 |

Phase 1 的问题解构和 Phase 4 的停滞/资源监控，用最少的代码带来最大的实际改善，这两块现在就可以加进去。Phase 2 的 ToT 策略规划和完整的元认知监控是中长期目标，等 Phase 3 的基础打扎实了再加。