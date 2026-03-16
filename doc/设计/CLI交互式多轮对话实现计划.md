# CLI 交互式多轮对话实现计划

> **需求**: 在 CLI（命令行）模式下，实现一个简单的控制台多轮对话交互循环（While-loop），实现一个让 Agent 保持上下文的多轮对话。

> **状态**: 规划完成，待实现

---

## 1. 需求分析

### 1.1 当前状态

| 组件 | 当前能力 | 说明 |
|------|---------|------|
| **RunCommand** | 单次消息交互 | 使用 `--chat "消息"` 参数，发送一次消息后进程退出 |
| **AgentExecutor** | 多轮对话支持 | 内部已使用 `ChatMemory` 存储对话历史 |
| **LangChainMemoryFactory** | 记忆管理 | 支持 `message_window` 和 `token_window` 两种记忆类型 |

**关键发现**: 运行时已经具备多轮对话记忆能力，只需在 CLI 层添加交互循环即可。

### 1.2 目标效果

```bash
# 交互式启动
./gradlew :agentdsl-cli:run --args="run examples/simple-chat.agent.groovy --interactive"

# 或简写
./gradlew :agentdsl-cli:run --args="run examples/simple-chat.agent.groovy -i"

# 预期输出:
# ═══════════════════════════════════════════════════════════
#  🤖 AgentDSL 交互式对话模式
#  加载脚本: examples/simple-chat.agent.groovy
#  Agent: hello-bot
#  输入 /help 查看命令帮助
# ═══════════════════════════════════════════════════════════
# 
# [hello-bot] 你好！有什么可以帮助你的？
# > 你好，请介绍一下你自己
# 
# [Agent] 你好！我是一个...
# 
# > 帮我查一下今天的天气
# 
# [Agent] 抱歉，我无法...
# 
# > /exit
# 
# 👋 对话结束，会话历史已保存
```

---

## 2. 技术方案

### 2.1 改动范围

| 模块 | 文件 | 改动类型 |
|------|------|---------|
| `agentdsl-cli` | `RunCommand.java` | **修改** - 添加交互模式 |
| `agentdsl-cli` | `InteractiveSession.java` (新建) | **新增** - 交互会话封装 |
| `agentdsl-runtime` | `AgentDslEngine.java` | 无改动 (已支持) |
| `agentdsl-runtime` | `AgentExecutor.java` | 无改动 (已支持) |

### 2.2 实现思路

核心是一个 `while` 循环:

```java
// 伪代码
while (running) {
    String userInput = readLine("> ");
    
    if (isExitCommand(userInput)) {
        break;
    }
    
    String response = engine.chat(agentName, userInput);
    printResponse(response);
}
```

**关键点**:
1. **复用现有记忆**: `AgentExecutor.chat()` 内部已处理 `instance.getMemory().add(userMsg)` 和 `instance.getMemory().add(aiMessage)`
2. **单次 Engine 实例**: 整个交互过程复用同一个 `AgentDslEngine` 实例，内存中的 `ChatMessage` 列表自动保持上下文
3. **优雅退出**: 支持 `/exit`, `/quit`, `q`, `exit`, `quit` 命令

---

## 3. 详细设计

### 3.1 新增 CLI 参数

在 `RunCommand.java` 中添加:

```java
@Option(names = { "--interactive", "-i" }, 
        description = "交互式多轮对话模式，从控制台读取输入（忽略 --chat）", 
        defaultValue = "false")
private boolean interactive;
```

### 3.2 InteractiveSession 核心逻辑

新建 `InteractiveSession.java`:

```java
public class InteractiveSession {
    
    private final AgentDslEngine engine;
    private final String agentName;
    private final BufferedReader reader;
    private final PrintWriter writer;
    
    public void run() {
        printWelcome();
        
        while (true) {
            String input = readInput();
            
            if (isExitCommand(input)) {
                printGoodbye();
                break;
            }
            
            if (isHelpCommand(input)) {
                printHelp();
                continue;
            }
            
            if (isClearCommand(input)) {
                clearScreen();
                continue;
            }
            
            if (input.isBlank()) {
                continue;
            }
            
            // 发送消息并获取回复
            String response = engine.chat(agentName, input);
            printResponse(response);
        }
    }
    
    // ... 辅助方法
}
```

### 3.3 交互命令支持

| 命令 | 说明 |
|------|------|
| `/exit`, `/quit`, `q` | 退出对话 |
| `/help` | 显示帮助信息 |
| `/clear` | 清屏 |
| `/history` | 显示对话历史 (可选) |
| `/restart` | 重新加载 Agent (可选) |

### 3.4 与现有模式的互斥

- `--interactive` 与 `--chat` 互斥
- `--interactive` 与 `--workflow` 互斥
- `--interactive` 与 `--autonomous` 互斥

---

## 4. 实现步骤

### Step 1: 修改 RunCommand.java

1. 添加 `--interactive` 选项
2. 在 `call()` 方法中判断是否启用交互模式
3. 如果是交互模式，创建 `InteractiveSession` 并执行

### Step 2: 新建 InteractiveSession.java

1. 实现控制台输入读取 (使用 `BufferedReader` 或 `Console`)
2. 实现交互循环 `while (running)`
3. 处理退出命令
4. 添加美化输出 (ANSI 颜色、格式化)

### Step 3: 测试验证

1. 启动交互模式
2. 进行多轮对话
3. 验证上下文保持 (在第3轮提及第1轮的话题)
4. 测试退出命令

---

## 5. 边界情况处理

| 场景 | 处理方式 |
|------|---------|
| Ctrl+C | 捕获 `InterruptedException`，打印退出信息，退出循环 |
| 空输入 | 跳过本次循环，继续读取 |
| 脚本中无 Agent | 打印错误信息，退出 |
| 网络超时 | 捕获异常，打印错误，继续循环等待下一条输入 |
| 模型报错 | 打印错误信息，继续循环 |

---

## 6. 可选增强 (后续迭代)

1. **会话保存/恢复**: 支持将对话历史保存到文件，下次启动时恢复
2. **多 Agent 切换**: 交互过程中切换不同的 Agent
3. **语法高亮**: 对 Agent 输出中的代码块进行语法高亮
4. **Tab 补全**: 支持命令和 Agent 名称的 Tab 补全

---

## 7. 依赖与约束

- **Java 版本**: Java 17+
- **第三方库**: 无新增依赖 (使用标准库 `java.io.BufferedReader`)
- **构建工具**: Gradle (已有)
- **运行时依赖**: LangChain4j ChatMemory (已存在)

---

## 8. 文件清单

| 操作 | 文件路径 |
|------|---------|
| 修改 | `agentdsl-cli/src/main/java/com/agentdsl/cli/RunCommand.java` |
| 新建 | `agentdsl-cli/src/main/java/com/agentdsl/cli/InteractiveSession.java` |

---

## 9. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 控制台编码问题 | 中文输入输出乱码 | 使用 `System.console()` 或指定 UTF-8 编码 |
| 内存泄漏 | 长时间运行内存膨胀 | 依赖 LangChain4j 的 `MessageWindowChatMemory` 自动清理旧消息 |
| 线程阻塞 | `readLine()` 阻塞主线程 | 这是 CLI 场景，可接受；Web 场景需异步处理 |

---

**计划制定完成，准备开始实现？** 
