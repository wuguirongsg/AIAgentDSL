# Autonomous 模式交互式多轮对话实现计划

> **需求**: Autonomous 模式支持多轮交互对话，允许用户在自主执行完成后继续下达新任务，保持上下文记忆。

> **状态**: 规划完成，待实现

---

## 1. 当前状态分析

### 1.1 Autonomous 模式现状

| 组件 | 当前能力 | 说明 |
|------|---------|------|
| **AutonomousExecutor** | 单次执行 | 接收一个 `userGoal`，执行完整 ReAct 循环后返回 `AutonomousResult` |
| **UserInteraction** | 计划确认 + 继续确认 | 支持 `confirmPlan()`, `confirmContinue()`, `showProgress()` |
| **ChatMemory** | 已支持 | 每轮对话已自动保存到内存 |

### 1.2 问题

```
当前流程:
┌─────────────────────────────────────────────────┐
│  ./agentdsl run script.agent.groovy \          │
│      --autonomous "帮我查天气"                   │
└─────────────────────────────────────────────────┘
                    ↓
         ┌───────────────────────┐
         │ 执行 ReAct 循环       │
         │ (多轮工具调用)        │
         └───────────────────────┘
                    ↓
         ┌───────────────────────┐
         │ 返回结果 → 进程退出    │
         └───────────────────────┘
                    ↓
         ❌ 无法继续对话
```

### 1.3 目标效果

```
预期流程:
┌─────────────────────────────────────────────────┐
│  ./agentdsl run script.agent.groovy \           │
│      --autonomous --interactive                │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ 🤖 欢迎使用 Autonomous 交互模式                  │
│ 输入任务目标，或 /help 查看命令                  │
└─────────────────────────────────────────────────┘
                    ↓
➜ 帮我查一下今天的天气

🔄 步骤 1/10...
   🔧 web_search → 北京今天晴转多云，22°C...
✅ 任务完成

➜ 那明天呢？

🔄 步骤 1/10...
   🔧 web_search → 明天北京多云转阴，20°C...
✅ 任务完成

➜ /exit
👋 对话结束
```

---

## 2. 设计方案

### 2.1 改动范围

| 模块 | 文件 | 改动类型 |
|------|------|---------|
| `agentdsl-cli` | `RunCommand.java` | 添加 `--autonomous --interactive` 组合支持 |
| `agentdsl-runtime` | `AutonomousExecutor.java` | 添加 `executeInteractive()` 方法 |
| `agentdsl-runtime` | `InteractiveAutonomousSession.java` (新建) | 封装交互逻辑 |

### 2.2 核心思路

复用现有的 `ChatMemory` 机制，在每次执行完成后：
1. 询问用户是否继续
2. 如果继续，读取新目标
3. 复用同一个 `AgentInstance`（内存中的消息历史自动保持）

### 2.3 实现方案

#### 方案 A: 在 AutonomousExecutor 中添加交互模式（推荐）

在 `AutonomousExecutor` 中添加一个方法：

```java
public void executeInteractive(AgentInstance instance, UserInteraction ui) {
    // 打印欢迎信息
    
    while (true) {
        // 1. 读取用户输入的新目标
        String goal = ui.readGoal();
        
        if (isExitCommand(goal)) break;
        
        // 2. 执行自主任务（复用现有 execute 方法）
        AutonomousResult result = execute(instance, goal);
        
        // 3. 显示结果
        ui.showResult(result);
        
        // 4. 询问是否继续（复用 confirmContinue 逻辑，或新增方法）
    }
}
```

#### 方案 B: 创建新的 InteractiveAutonomousSession

将交互逻辑抽取到独立类中，保持 AutonomousExecutor 单一职责。

**推荐方案 A**，改动最小，复用现有逻辑。

### 2.4 UserInteraction 接口扩展

需要在 `UserInteraction` 中添加两个方法：

```java
// 读取用户输入的目标
String readGoal();

// 显示执行结果
void showResult(AutonomousResult result);
```

### 2.5 ConsoleUserInteraction 实现

在 `ConsoleUserInteraction` 中实现这两个方法：

```java
@Override
public String readGoal() {
    System.out.print("\n➜ ");
    return reader.readLine();
}

@Override
public void showResult(AutonomousResult result) {
    System.out.println("\n" + "═".repeat(60));
    System.out.println(result.getFinalAnswer());
    System.out.println("═".repeat(60));
    System.out.printf("📊 执行了 %d 步，%s%n",
            result.getTotalSteps(),
            result.isCompleted() ? "✅ 目标已完成" : "⚠️ " + result.getTerminationReason());
}
```

---

## 3. 详细设计

### 3.1 RunCommand 改动

添加组合选项检测：

```java
@Option(names = { "--autonomous", "--auto" }, description = "以自主模式执行，指定任务目标描述")
private String autonomousGoal;

@Option(names = { "--interactive" }, description = "交互式多轮对话模式")
private boolean interactive;
```

在 `call()` 方法中：

```java
// 交互式 + 自主模式
if (interactive && autonomousGoal == null) {
    String targetAgent = resolveTargetAgent(engine);
    if (targetAgent == null) return 1;
    
    AutonomousExecutor executor = engine.getAutonomousExecutor();
    // 需要暴露 executor 或添加新方法
    executor.executeInteractive(targetAgent, engine, userInteraction);
    return 0;
}
```

### 3.2 AutonomousExecutor 改动

添加新方法：

```java
public void executeInteractive(String agentName, AgentDslEngine engine, UserInteraction ui) {
    AgentInstance instance = engine.getRegistry().get(agentName);
    if (instance.getSpec().getAutonomous() == null) {
        throw new DslRuntimeException("ADSL-030",
                "Agent '" + agentName + "' 未配置 autonomous 模式");
    }
    
    ui.showWelcome();
    
    while (true) {
        String goal = ui.readGoal();
        if (ui.isExitCommand(goal)) {
            ui.showGoodbye();
            break;
        }
        
        if (goal.isBlank()) continue;
        
        // 执行并显示结果
        AutonomousResult result = execute(instance, goal);
        ui.showResult(result);
    }
}
```

### 3.3 交互命令

| 命令 | 说明 |
|------|------|
| `/exit`, `/quit`, `q` | 退出 |
| `/help` | 显示帮助 |
| `/clear` | 清屏 |
| `/status` | 显示当前会话状态 |

---

## 4. 实现步骤

### Step 1: 扩展 UserInteraction 接口

在 `UserInteraction.java` 中添加：

```java
String readGoal();
void showResult(AutonomousResult result);
void showWelcome();
void showGoodbye();
boolean isExitCommand(String input);
```

### Step 2: 实现 ConsoleUserInteraction

在 `ConsoleUserInteraction.java` 中实现新方法。

### Step 3: 修改 AutonomousExecutor

添加 `executeInteractive()` 方法。

### Step 4: 修改 RunCommand

添加 `--autonomous --interactive` 组合支持。

### Step 5: 测试验证

---

## 5. 边界情况处理

| 场景 | 处理方式 |
|------|---------|
| Agent 未配置 autonomous | 报错退出 |
| 空输入 | 跳过，继续读取 |
| Ctrl+C | 捕获异常，优雅退出 |
| 执行失败 | 显示错误信息，继续等待下一条输入 |

---

## 6. 文件清单

| 操作 | 文件路径 |
|------|---------|
| 修改 | `agentdsl-runtime/.../autonomous/UserInteraction.java` |
| 修改 | `agentdsl-runtime/.../autonomous/ConsoleUserInteraction.java` |
| 修改 | `agentdsl-runtime/.../autonomous/AutonomousExecutor.java` |
| 修改 | `agentdsl-cli/.../RunCommand.java` |

---

## 7. 风险评估

| 风险 | 影响 | 缓解 |
|------|------|------|
| 长时间运行内存膨胀 | 记忆无限增长 | 依赖 `MessageWindowChatMemory` 自动清理 |
| 执行失败导致循环中断 | 用户体验 | 捕获异常，继续循环 |

---

**是否开始实现？**
