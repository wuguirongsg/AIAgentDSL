# Workflow 直接调用工具/执行代码 — 设计方案

## 1. 背景与动机

当前 AgentDSL 的 Workflow 中，所有动作都必须经过 Agent（大模型）去调度。这在工业和企业场景下是一种**架构反模式**（"交大模型税 / LLM Tax"）：

| 痛点 | 说明 |
|------|------|
| **成本与延迟浪费** | 确定性任务（查数据库、格式化 JSON）不需要大模型"思考"，每次消耗 Token 且等待数秒 |
| **幻觉风险** | 纯逻辑操作交给 LLM 有错误概率，在企业流水线中不可接受 |
| **Plan 模式脱节** | 人类审批后的执行计划需要确定性执行，此时 LLM 已不再参与 |

**核心目标**：将 Workflow 提升为最高调度者，Agent 只是其中一个"认知节点"。

## 2. 设计原则

- **向后兼容**：现有 `step { agent "xxx" }` 语法不变
- **控制流与执行机制正交**：`StepType`（SEQUENTIAL/PARALLEL/CONDITION/LOOP）描述控制流，执行模式（agent/execute/tool/skill/mcp）描述执行机制，二者独立
- **复用现有基础设施**：复用 `AgentRegistry` 的 `toolCallResolver`、`globalTools`、`globalSkills`、`McpToolProviderBridge`

## 3. DSL 语法设计

### 3.1 五种执行模式

```groovy
workflow "每日退款工单处理流" {
    steps {
        // ===== 模式 1: 纯代码执行节点（0 LLM 参与）=====
        step "获取待处理数据" {
            execute { ctx ->
                def rawData = ctx.toolCall("db_query", [sql: "SELECT * FROM tickets WHERE status='OPEN'"])
                return rawData
            }
        }

        // ===== 模式 2: 直接调用工具节点 =====
        step "发送通知邮件" {
            tool "http_post"
            input { lastOutput ->
                [url: "https://api.example.com/notify", body: lastOutput]
            }
        }

        // ===== 模式 3: 直接调用 Skill 节点 =====
        step "执行退款" {
            skill "ERP_同意退款"
            input { lastOutput ->
                [id: lastOutput.id, amount: lastOutput.amount]
            }
        }

        // ===== 模式 4: Agent 认知节点（现有功能不变）=====
        step "分析退款合理性" {
            agent "售后审核大脑"
            input { lastOutput -> "请分析以下退款申请的合理性: ${lastOutput}" }
        }

        // ===== 模式 5: 直接调用 MCP 工具节点 =====
        step "读取 GitHub 文件" {
            mcp "github_mcp", "get_file_contents"
            input { lastOutput ->
                [repo: "company/finance", path: "2026-03/report.pdf"]
            }
        }
    }
}
```

### 3.2 互斥约束

`step` 内部的 `agent` / `execute` / `tool` / `skill` / `mcp` **五选一**，编译期校验互斥。

### 3.3 与控制流的组合

所有控制流结构（`parallel`、`condition`、`loop`）内部的 step 都可以使用任意执行模式：

```groovy
parallel {
    step "并行查数据" { execute { ctx -> ... } }
    step "并行调工具" { tool "http_get"; input { ... } }
}

loop(maxIterations: 5) {
    step "循环处理" { execute { ctx -> ... } }
    until { lastOutput -> lastOutput == "done" }
}
```

## 4. 模型层改动

### 4.1 StepSpec.java

新增字段（与 `agentRef` 互斥）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `executeClosure` | `Closure<?>` | 纯代码执行闭包 |
| `toolRef` | `String` | 直接调用的工具名称 |
| `skillRef` | `String` | 直接调用的 Skill 名称 |
| `mcpServerRef` | `String` | 直接调用的 MCP 服务器名称 |
| `mcpToolRef` | `String` | 直接调用的 MCP 工具名称 |

### 4.2 StepDelegate.groovy

新增 DSL 关键字：

| 关键字 | 说明 |
|--------|------|
| `execute(Closure)` | 设置纯代码执行闭包 |
| `tool(String)` | 设置直接调用的工具名称 |
| `skill(String)` | 设置直接调用的 Skill 名称 |
| `mcp(String, String)` | 设置 MCP 服务器名和工具名 |

## 5. 新增上下文类

### 5.1 WorkflowExecutionContext.java

作为 `execute` 闭包的 delegate，提供：

- 访问 `WorkflowContext`（`lastOutput`、`stepResults`）
- `toolCall(name, params)` — 调用已注册工具
- `mcpCall(server, tool, params)` — 调用 MCP 工具

## 6. 执行引擎改动

### 6.1 WorkflowExecutor.executeSequential() 重构

```
executeSequential(step, ctx)
  ├── executeClosure != null  → executeCodeBlock()
  ├── toolRef != null         → executeDirectToolCall()
  ├── skillRef != null        → executeDirectSkillCall()
  ├── mcpServerRef != null    → executeDirectMcpCall()
  ├── agentRef != null        → executeAgentCall() （现有逻辑）
  └── else                    → 抛异常
```

### 6.2 AgentRegistry 新增方法

| 方法 | 说明 |
|------|------|
| `executeToolDirectly(name, params)` | 直接执行已注册工具 |
| `executeSkillDirectly(name, params)` | 直接执行已注册 Skill |
| `executeMcpToolDirectly(server, tool, params)` | 直接调用 MCP 工具 |
| `getToolCallResolver()` | 暴露 toolCallResolver 给 WorkflowExecutor |

## 7. 数据流

```
WorkflowExecutor
│
├─ step "A" (execute)
│   └─ WorkflowExecutionContext
│       ├─ ctx.lastOutput → 上一步输出
│       ├─ ctx.toolCall() → AgentRegistry.executeToolDirectly()
│       └─ return result → ctx.putStepResult("A", result)
│
├─ step "B" (tool)
│   ├─ input { lastOutput → params Map }
│   └─ AgentRegistry.executeToolDirectly("toolName", paramsMap)
│       └─ result → ctx.putStepResult("B", result)
│
├─ step "C" (agent) ← 现有逻辑不变
│   ├─ input { lastOutput → String }
│   └─ AgentExecutor.chat("agentName", inputString)
│       └─ result → ctx.putStepResult("C", result)
│
└─ step "D" (mcp)
    ├─ input { lastOutput → params Map }
    └─ McpToolProviderBridge.callToolDirectly(server, tool, params)
        └─ result → ctx.putStepResult("D", result)
```

## 8. 校验规则

- SEQUENTIAL 步骤必须且只能指定一种执行模式
- `toolRef` 引用的工具必须为已注册工具或内置工具
- `skillRef` 引用的 Skill 必须已注册
- `mcpServerRef` 引用的 MCP 服务器必须在全局已声明
- `execute` 闭包不能与 `agent`/`tool`/`skill`/`mcp` 共存

## 9. 可观测性

新增 `DebugEvent.Type`：

| 事件类型 | 说明 |
|---------|------|
| `CODE_EXECUTE` | 纯代码执行开始/结束 |
| `DIRECT_TOOL_CALL` | 直接工具调用 |
| `DIRECT_SKILL_CALL` | 直接 Skill 调用 |
| `DIRECT_MCP_CALL` | 直接 MCP 调用 |

## 10. 涉及修改的文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `StepSpec.java` | 修改 | 新增 5 个字段及 getter/setter |
| `StepDelegate.groovy` | 修改 | 新增 4 个 DSL 关键字方法 |
| `WorkflowExecutionContext.java` | 新增 | execute 闭包委托上下文 |
| `WorkflowExecutor.java` | 修改 | 重构 executeSequential 分发逻辑 |
| `AgentRegistry.java` | 修改 | 暴露直接执行方法 |
| `DslValidator.java` | 修改 | 新增步骤执行模式互斥校验 |
| `DebugEvent.java` | 修改 | 新增追踪事件类型 |

## 11. 设计决策

**Q: 为什么不新增 StepType 枚举？**
`StepType` 描述控制流（顺序/并行/条件/循环），执行机制（agent/execute/tool/skill/mcp）是正交维度。将二者分离后，`parallel`/`loop`/`condition` 内部也能自由使用新执行模式。

**Q: `input` 闭包语义变化如何处理？**
对 `agent` 模式返回 String（聊天消息），对 `tool`/`skill`/`mcp` 模式返回 Map（工具参数）。由执行引擎内部的类型转换方法处理。

**Q: MCP 直接调用是否需要？**
是。MCP 作为跨语言微服务总线，直接调用可实现 100% 确定性、0 Token 损耗的跨语言调度。适合低频重型任务（操作浏览器、Python 脚本、外部 API）。
