仔细读完了设计框架文档和你的现有实现，来做一个系统性的对比分析。

---

## 一、现有实现的问题诊断

先对照设计框架，把问题分级说清楚。

### 🔴 高优先级问题

**① ReAct 循环缺少 Reflect 步骤，是个残缺的 ReAct**

你的代码注释写的是 `Act → Observe → Reflect`，但实现里根本没有 Reflect。循环体实际只做了：

```java
// 现有实现的实际逻辑
while(true) {
    调用 LLM → 有工具调用? → 执行工具 → 继续
                           ↓ 没有工具调用
                         认为任务完成，退出
}
```

"没有工具调用"就等于"任务完成"这个假设是错的。LLM 可能是因为困惑、卡住或上下文不足而不调用工具，不是因为真正完成了任务。缺少反思步骤，Agent 无法自我纠错。

**② 没有失败分类，所有错误一视同仁**

设计框架明确要求区分三类失败：
- 知识不足（需要更多信息）
- 策略错误（需要换路径）
- 工具失败（需要重试或换工具）

你现在的代码对所有失败的处理都是：
```java
toolResult = "Error executing tool: " + e.getMessage();
// 然后继续下一轮，LLM 自己想办法
```

LLM 看到一个裸错误消息，不知道应该重试、换方法还是放弃，很容易陷入无效循环。

**③ 置信度/进度停滞检测完全缺失**

设计框架里的元认知监控表：

| 监控维度 | 触发条件 | 应对动作 |
|---|---|---|
| 进度停滞 | 3步内无进展 | 切换策略 |
| 置信度下降 | 分数连续下滑 | 回溯重规划 |

你的实现里，如果 Agent 在原地打转（每步都在调同一个工具，得到同样的失败），唯一的保护机制是等 `max_steps` 耗尽，然后问用户要不要继续。这是一个非常被动的止损。

**④ `buildReActSystemPrompt` 把整个工具列表拼进去，不是真正的 ReAct Prompt**

真正的 ReAct Prompt 需要明确引导 LLM 按照 Thought / Action / Observation 格式输出，让推理过程显式化。你现在的 Prompt 只是列了工具描述，没有引导结构化思考。

---

### 🟡 中优先级问题

**⑤ 消息历史无压缩，长任务必然 token 超限**

```java
List<ChatMessage> messages = new ArrayList<>();
// 每一步的工具结果都完整追加
messages.add(resultMsg);
```

一个 20 步的任务，每步工具返回 1000 token，消息历史就是 20000+ token。大多数模型的上下文窗口撑不住，而且早期步骤的详细信息到后期往往已经没用了。

**⑥ PlannerEngine 生成计划后，计划和实际执行是脱节的**

Plan 模式的流程：
1. 生成计划 ✅
2. 用户确认 ✅  
3. 执行 ReAct 循环 ✅
4. **计划和 ReAct 循环是完全独立的** ❌

执行时 ReAct 根本不参考计划，计划仅用于展示给用户确认。如果 LLM 在执行时走了和计划完全不同的路径，系统也不会发现或修正。

**⑦ 工作记忆和对话记忆混用**

```java
// 现有代码把工具执行结果直接加进对话记忆
if (instance.getMemory() != null) {
    instance.getMemory().add(resultMsg);
}
```

对话记忆（`ChatMemory`）是为多轮对话设计的，保存的是用户和 AI 的对话历史。把工具执行的中间结果也塞进去，会污染对话记忆，导致 `message_window` 的窗口被工具结果占满。

---

### 🟢 低优先级问题

**⑧ `dynamicDiscoveryAttempts < 1` 的限制过于粗糙**

每轮最多发现 1 次 MCP 工具，但没有区分是"本任务已发现过"还是"本步骤已发现过"。

**⑨ `buildProgressSummary` 只截取 action，没有 observation**

用户在 max_steps 暂停时看到的进度摘要，只有"做了什么"，没有"结果是什么"，无法做出有效的是否继续的判断。

---

## 二、具体优化方案

### 方案一：补全 Reflect 节点，让 ReAct 真正完整

在每次 Observe 之后，增加一个轻量级的自我评估步骤。实现上不需要单独调用 LLM，而是在下一轮的 System Prompt 中注入反思引导，让 LLM 在下一个 Thought 里自然包含反思。

关键是修改 `buildReActSystemPrompt`，让它引导结构化输出：

```java
private String buildReActSystemPrompt(String agentSystemPrompt, 
                                       String userGoal,
                                       List<ToolSpecification> tools) {
    String toolDescriptions = tools.stream()
        .map(t -> String.format("- %s: %s", t.name(), t.description()))
        .collect(Collectors.joining("\n"));

    String basePrompt = agentSystemPrompt != null 
        ? agentSystemPrompt + "\n\n" : "";

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
                      toolDescriptions.isEmpty() ? "（无工具，仅使用推理能力）" 
                                                 : toolDescriptions);
}
```

---

### 方案二：增加进度停滞检测器

这是元认知监控的核心，实现一个轻量的 `StagnationDetector`：

```java
/**
 * 检测 ReAct 循环是否陷入停滞。
 * 判断标准：连续 N 步调用相同工具且返回相似结果。
 */
private static class StagnationDetector {
    
    private static final int STAGNATION_WINDOW = 3; // 连续几步判定停滞
    private final Deque<String> recentActions = new ArrayDeque<>();

    /**
     * 记录本步行动，返回是否检测到停滞。
     */
    public boolean record(List<ToolExecutionRequest> requests) {
        if (requests.isEmpty()) return false;
        
        // 用工具名+参数摘要作为行动指纹
        String actionFingerprint = requests.stream()
            .map(r -> r.name() + ":" + r.arguments().hashCode())
            .collect(Collectors.joining("|"));
        
        recentActions.addLast(actionFingerprint);
        if (recentActions.size() > STAGNATION_WINDOW) {
            recentActions.removeFirst();
        }
        
        // 窗口满了且所有行动完全相同 → 停滞
        return recentActions.size() == STAGNATION_WINDOW
            && recentActions.stream().distinct().count() == 1;
    }

    public void reset() {
        recentActions.clear();
    }
}
```

在 ReAct 主循环里使用：

```java
StagnationDetector stagnationDetector = new StagnationDetector();

// ... 在工具调用之后 ...
if (aiMessage.hasToolExecutionRequests()) {
    
    // ✅ 停滞检测
    if (stagnationDetector.record(aiMessage.toolExecutionRequests())) {
        log.warn("[{}] 检测到执行停滞，连续 {} 步相同操作", 
                 agentName, STAGNATION_WINDOW);
        
        // 注入策略切换引导
        messages.add(UserMessage.from(
            "[系统提示] 你已连续 " + STAGNATION_WINDOW + 
            " 步执行相同操作但未取得进展。" +
            "请重新分析问题，考虑：\n" +
            "1. 当前路径是否可行？\n" +
            "2. 是否需要换一种工具或方法？\n" +
            "3. 是否需要拆分任务为更小的子目标？"
        ));
        stagnationDetector.reset();
        continue; // 重新让 LLM 思考，不退出循环
    }
}
```

---

### 方案三：失败分类处理

把裸异常消息包装成结构化的失败反馈：

```java
/**
 * 工具执行失败的分类和处理。
 */
private String executeToolWithClassifiedError(ToolExecutor executor,
                                               ToolExecutionRequest request,
                                               String agentName) {
    try {
        return executor.execute(request, agentName);
        
    } catch (Exception e) {
        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        FailureType type = classifyFailure(e, errorMsg);
        
        // 根据失败类型给 LLM 不同的恢复建议
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
                
            default -> String.format(
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

private enum FailureType {
    NETWORK, PERMISSION, NOT_FOUND, TIMEOUT, UNKNOWN
}
```

---

### 方案四：消息历史压缩

长任务下防止 token 超限，引入一个简单的滑动窗口压缩策略：

```java
/**
 * 工具执行结果的上下文压缩器。
 * 策略：保留最近 N 步的完整结果，更早的结果压缩为摘要。
 */
private static class ContextCompressor {
    
    private static final int FULL_DETAIL_STEPS = 5;   // 最近5步保留完整内容
    private static final int MAX_TOOL_RESULT_LENGTH = 500; // 超过此长度则截断
    
    /**
     * 压缩一条工具执行结果，根据它距当前步骤的远近决定压缩程度。
     */
    public String compress(String toolResult, int currentStep, int resultStep) {
        int age = currentStep - resultStep;
        
        if (age <= FULL_DETAIL_STEPS) {
            // 近期结果：截断超长内容，但保留结构
            return toolResult.length() > MAX_TOOL_RESULT_LENGTH
                ? toolResult.substring(0, MAX_TOOL_RESULT_LENGTH) + "\n...[结果已截断]"
                : toolResult;
        } else {
            // 较早的结果：只保留关键摘要
            String firstLine = toolResult.lines().findFirst().orElse(toolResult);
            return "[步骤" + resultStep + "摘要] " + 
                   (firstLine.length() > 100 ? firstLine.substring(0, 100) + "..." : firstLine);
        }
    }
}
```

在构建消息列表时，对早期的工具结果使用压缩后的版本，而不是原始完整内容。

---

### 方案五：计划与执行对齐

让 ReAct 循环在执行时能"感知"当前的计划，在 System Prompt 里注入计划上下文，并在 Reflect 步骤里引导 LLM 检查是否偏离计划：

```java
// 在 executePlanMode 里，把 plan 传给 ReAct 循环
private AutonomousResult executePlanMode(...) {
    // ... 生成计划、用户确认 ...
    
    // ✅ 把计划传进去，让执行和计划对齐
    return executeReActLoop(instance, userGoal, config, plan);
}

// ReAct 开始时把计划注入 System Prompt
private String buildReActSystemPromptWithPlan(String agentSystemPrompt,
                                               String userGoal,
                                               List<ToolSpecification> tools,
                                               ExecutionPlan plan) {
    String planContext = plan != null 
        ? "\n### 预定执行计划\n" + plan.toDisplayString() + 
          "\n请按照此计划执行，如需偏离请在 Reflect 中说明原因。\n"
        : "";
    
    // 合并进 System Prompt
    return buildReActSystemPrompt(agentSystemPrompt, userGoal, tools) + planContext;
}
```

---

## 三、优化后的整体结构

把以上方案整合起来，`executeReActLoop` 的核心结构变成这样：

```
初始化
  ├── StagnationDetector
  ├── ContextCompressor  
  └── 注入带计划的 ReAct System Prompt

while(true):
  ├── max_steps 检查（暂停询问，不是报错）
  ├── 调用 LLM
  ├── 解析输出
  │   ├── 包含 TASK_COMPLETE → 结束，提取最终答案
  │   └── 包含工具调用 → 继续
  ├── StagnationDetector.record()
  │   └── 停滞 → 注入策略切换引导，continue
  ├── 执行工具（带失败分类）
  │   └── 失败 → 返回结构化错误消息（含恢复建议）
  ├── 压缩历史消息（ContextCompressor）
  └── 记录 StepResult（含 action + observation）
```

---

## 四、优先级排序建议

如果一次做不完，按这个顺序来：

| 优先级 | 优化项 | 理由 |
|---|---|---|
| 1️⃣ | 修复 ReAct Prompt，引导结构化思考 | 直接影响每次执行质量，改动小 |
| 2️⃣ | 补完 TASK_COMPLETE 终止信号 | 防止"任务完成但继续循环"的问题 |
| 3️⃣ | 失败分类 + 结构化错误消息 | 大幅提升 Agent 自我恢复能力 |
| 4️⃣ | 停滞检测 | 解决原地打转的问题 |
| 5️⃣ | 消息历史压缩 | 长任务必须，短任务可以暂缓 |
| 6️⃣ | 计划与执行对齐 | 提升 plan 模式的实际价值 |

前三项是改动最小、收益最大的，建议优先做。后三项是进一步增强，可以放在 v1.5 的 Autonomous 增强迭代里。