# AgentDSL 项目架构设计与开发指导

> **文档版本**: v1.0 &nbsp;|&nbsp; **最后更新**: 2026-03-12
>
> **目标读者**: AI 智能体（二次开发）、项目贡献者、架构师
>
> **设计意图**: 本文档旨在提供一份结构化的项目地图，使开发者（包括 AI 智能体）无需通读全部源码，即可快速定位模块职责、理解数据流向、掌握扩展方法。

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 技术栈](#2-技术栈)
- [3. 模块总览与依赖关系](#3-模块总览与依赖关系)
- [4. 端到端执行管道](#4-端到端执行管道)
- [5. 各模块详细说明](#5-各模块详细说明)
  - [5.1 agentdsl-core](#51-agentdsl-core)
  - [5.2 agentdsl-compiler](#52-agentdsl-compiler)
  - [5.3 agentdsl-langchain4j](#53-agentdsl-langchain4j)
  - [5.4 agentdsl-tools](#54-agentdsl-tools)
  - [5.5 agentdsl-mcp](#55-agentdsl-mcp)
  - [5.6 agentdsl-runtime](#56-agentdsl-runtime)
  - [5.7 agentdsl-cli](#57-agentdsl-cli)
- [6. 核心设计模式](#6-核心设计模式)
- [7. 扩展开发指南](#7-扩展开发指南)
- [8. 项目目录快速索引](#8-项目目录快速索引)
- [9. 常见开发场景速查](#9-常见开发场景速查)

---

## 1. 项目概述

**AgentDSL** 是 Java 生态下的企业级 AI Agent 领域特定语言（DSL）。开发者通过编写 `.agent.groovy` 脚本，以声明式方式定义 Agent、工具（Tool）、技能（Skill）、工作流（Workflow），由框架负责编译、校验、注册并驱动 LangChain4j 执行。

核心价值：
- **声明式 DSL** — 用 Groovy 闭包语法描述 Agent "是什么"
- **MCP 一等公民** — 直接挂载任何标准 MCP Server 作为 Agent 工具
- **确定性逻辑技能** — 支持 Prompt 技能和 Logic 技能，业务流程 100% 可控
- **安全沙箱** — 编译期 AST 级别安全限制，防止未授权系统访问
- **全链路可观测** — `--debug` 追踪从 Agent 调用到工具执行的完整链路

---

## 2. 技术栈

| 层面 | 技术 | 版本 |
|------|------|------|
| 语言 | Java + Groovy | Java 17 / Groovy 4.0.27 |
| 构建 | Gradle (Kotlin DSL) | 见 `gradle/wrapper` |
| LLM 集成 | LangChain4j | 1.11.0 |
| MCP 协议 | langchain4j-mcp | 1.0.0-beta5 |
| CLI 框架 | picocli | 4.7.6 |
| HTTP 客户端 | OkHttp3 | 4.12.0 |
| 数据处理 | Apache POI (Excel) / PDFBox (PDF) | 5.2.5 / 3.0.2 |
| 浏览器自动化 | Playwright | 1.48.0 |
| 搜索 | Tavily (langchain4j) | 0.36.2 |
| 日志 | SLF4J + Logback | 2.0.16 / 1.5.16 |
| 测试 | JUnit 5 + Mockito + WireMock | 5.11.4 / 5.14.2 / 3.9.1 |

---

## 3. 模块总览与依赖关系

### 3.1 七大模块职责一句话总结

| 模块 | 一句话职责 |
|------|-----------|
| **agentdsl-core** | DSL 语法定义层 — Groovy Delegate + Java Spec 模型 + 注解 + 异常 + 指标 |
| **agentdsl-compiler** | DSL 编译与校验 — 把 `.agent.groovy` 解析为 Spec 对象并进行语义校验 |
| **agentdsl-langchain4j** | LangChain4j 桥接层 — 把 Spec 转换为 ChatModel / ChatMemory / ContentRetriever / ToolExecutor |
| **agentdsl-tools** | 内置工具库 — HTTP、JSON、文件、Excel、PDF、图片、命令行、数据库、搜索、浏览器 |
| **agentdsl-mcp** | MCP 桥接层 — 把 MCP Server 的工具转换为 LangChain4j ToolSpecification + ToolExecutor |
| **agentdsl-runtime** | 执行引擎 — Agent 生命周期管理、ReAct 循环、工作流编排、自主模式、热加载 |
| **agentdsl-cli** | 命令行入口 — run / validate / list 三个子命令 |

### 3.2 模块依赖关系图

```
                        agentdsl-cli
                            │
                            ▼
                      agentdsl-runtime
                     ╱    │    │    ╲
                    ╱     │    │     ╲
                   ▼      ▼    ▼      ▼
    agentdsl-langchain4j  │  agentdsl-mcp  agentdsl-tools
                   │      │      │
                   ▼      ▼      ▼
                    agentdsl-compiler
                          │
                          ▼
                     agentdsl-core
```

**依赖规则**：
- `agentdsl-core` 是零外部依赖的基础层，所有模块都依赖它
- `agentdsl-compiler` 仅依赖 `core`
- `agentdsl-langchain4j`、`agentdsl-tools`、`agentdsl-mcp` 分别依赖 `core`
- `agentdsl-runtime` 聚合所有子模块，是执行入口
- `agentdsl-cli` 依赖 `runtime` + `compiler` + `core`

---

## 4. 端到端执行管道

当用户执行 `agentdsl run script.agent.groovy --chat "你好"` 时，系统经历以下阶段：

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ 阶段 1: 解析 (Parsing)                                                          │
│   DslCompiler.compileFile(script.agent.groovy)                                   │
│     → GroovyShell.parse() 创建 DslBaseScript 实例                                │
│     → script.run() 触发 agent()/tool()/workflow()/skill()/datasource() 调用       │
│     → 各 Delegate 解析 Closure → 生成 Spec 对象                                   │
│   输出: List<AgentSpec>, List<ToolSpec>, List<WorkflowSpec>, ...                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│ 阶段 2: 校验 (Validation)                                                        │
│   DslValidator.validateAll(agents, tools, workflows, skills)                      │
│     → 必填项、引用存在性、参数约束、执行模式互斥等校验                                │
│   输出: DslCompileResult (含 Diagnostics)                                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│ 阶段 3: 注册 (Registration)                                                      │
│   AgentDslEngine.load() → AgentRegistry                                           │
│     → registerTools(): ToolScanner + BuiltinToolRegistry → ToolSpec 列表           │
│     → registerSkills(): Prompt/Logic Skill → 展平为 ToolSpec                       │
│     → register(agentSpec): 创建 AgentInstance                                     │
│       → LangChainModelFactory.create() → ChatModel                                │
│       → LangChainMemoryFactory.create() → ChatMemory                              │
│       → LangChainToolBridge.convertAll() → ToolSpecification + ToolExecutor        │
│       → McpToolProviderBridge.connect() → MCP 工具                                 │
│       → LangChainRagFactory.create() → ContentRetriever (可选)                     │
│   输出: AgentInstance (含 model, memory, tools, rag)                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│ 阶段 4: 执行 (Execution)                                                         │
│   AgentExecutor.chat(agentName, userMessage)                                      │
│     → 构造消息: SystemPrompt + Memory + UserMessage (+ RAG 增强)                   │
│     → ReAct 工具调用循环 (最多 10 轮):                                              │
│       → 调用 ChatModel → 若返回 ToolExecutionRequest → 执行工具 → 结果加入消息      │
│       → 若返回纯文本 → 结束并返回                                                   │
│     → 支持 MCP 自动发现 (auto_discover_mcp)                                        │
│   输出: 最终文本响应                                                                │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 4.1 工作流执行流程

```
WorkflowExecutor.execute(workflowName, input)
  → 遍历 StepSpec 列表:
    ├── SEQUENTIAL 步骤:
    │   ├── agent → AgentExecutor.chat()
    │   ├── execute → 执行 Groovy Closure (WorkflowExecutionContext)
    │   ├── tool → AgentRegistry.executeToolDirectly()
    │   ├── skill → AgentRegistry.executeSkillDirectly()
    │   └── mcp → 直接调用 MCP 工具
    ├── PARALLEL 步骤: ExecutorService 并行执行子步骤
    ├── CONDITION 步骤: 评估 check 条件 → 执行对应分支
    └── LOOP 步骤: 循环执行直到 until 条件满足
  → 输出: WorkflowResult (finalOutput + stepResults + executionTrace)
```

### 4.2 自主模式执行流程

```
AutonomousExecutor.execute(instance, userGoal)
  ├── Plan 模式:
  │   → PlannerEngine.generatePlan() → 用户确认
  │   → 若修改 → revisePlan() → 再次确认
  │   → 进入 ReAct 循环
  └── Fast 模式:
      → 直接进入 ReAct 循环
  → ReAct: LLM 调用 → 工具执行 → 结果反馈 → 循环 (受 max_steps 限制)
  → 输出: AutonomousResult
```

### 4.3 MCP 自动发现机制（auto_discover_mcp）

当 Agent 配置 `auto_discover_mcp true` 时，AutonomousExecutor 在启动 ReAct 循环前会尝试从 MCP 仓库动态发现并挂载工具。

**触发条件**：
1. Agent 的 `autonomous` 块中设置了 `auto_discover_mcp true`
2. Agent 当前**未配置任何工具**（`tools.isEmpty()`）
3. 当前任务会话中**尚未执行过自动发现**（每会话最多 1 次主动发现）

**执行流程**：

```
AutonomousExecutor.executeReActLoop()
  ├── 检查 tools.isEmpty() && autoDiscoverMcp
  │   └── 是 → AgentRegistry.tryAutoDiscoverAndAttachTool(goal)
  │       ├── 遍历所有已注册的 MCP Server
  │       ├── 使用 LLM 评估 server 的工具是否与 goal 匹配
  │       │   └── 提示词: "任务目标: {goal}, 工具列表: [...], 是否相关?"
  │       └── 若匹配 → 挂载该 server 到当前 Agent
  │           └── tools = Agent.getToolSpecifications() (更新)
  ├── 进入正常 ReAct 循环
  └── 若执行中遇到未识别的工具名
      └── 被动发现: tryAutoDiscoverAndAttachTool(toolName, goal)
          └── 按工具名精确匹配，尝试动态挂载
```

**主动发现 vs 被动发现**：

| 类型 | 触发时机 | 匹配方式 | 次数限制 |
|------|---------|---------|---------|
| **主动发现** | ReAct 循环开始前 | 基于 goal 语义匹配 | 每会话 1 次 |
| **被动发现** | 工具执行时遇到未识别工具 | 按 toolName 精确匹配 | 每轮 1 次 |

**安全机制**：
- 即使自动发现失败，Agent 仍会以无工具模式继续执行（降级处理）
- 已配置 tools 的 Agent 不会触发自动发现（避免覆盖用户配置）
- 发现过程有日志输出：`🔍 正在从 MCP 仓库中自动发现可用工具...`

---

## 5. 各模块详细说明

### 5.1 agentdsl-core

> **定位**: DSL 的语法定义与领域模型，是整个项目的基石。

**源码路径**: `agentdsl-core/src/main/`

```
groovy/com/agentdsl/core/dsl/     ← Groovy DSL Delegate 类（解析语法）
java/com/agentdsl/core/
  ├── annotation/                  ← @AgentTool, @ToolParam 工具注解
  ├── dsl/                         ← SkillExecutionContext
  ├── exception/                   ← DslCompilationException, DslRuntimeException
  ├── metrics/                     ← DebugTracer, DebugEvent, MetricsCollector, ToolMetrics
  ├── spec/                        ← 所有 Spec 模型（AgentSpec, ToolSpec 等）
  └── utils/                       ← ConvertUtils
```

**核心概念 — Delegate 与 Spec 的对应关系**:

DSL 解析采用 Groovy 闭包委托模式。每个 DSL 块（如 `agent { }`）对应一个 Delegate 类和一个 Spec 类：

| DSL 语法块 | Delegate (解析语法) | Spec (数据模型) |
|-----------|---------------------|----------------|
| `agent("name") { }` | AgentDelegate | AgentSpec |
| `model { }` | ModelDelegate | ModelSpec |
| `memory { }` | MemoryDelegate | MemorySpec |
| `tools { }` | ToolsBlockDelegate → ToolDelegate | ToolSpec |
| `skills { }` | SkillsBlockDelegate → SkillDelegate | SkillSpec |
| `mcp { }` | McpBlockDelegate → McpServerDelegate | McpSpec → McpServerSpec |
| `rag { }` | RagDelegate → ContentRetrieverDelegate | RagSpec → ContentRetrieverSpec |
| `guardrails { }` | GuardrailDelegate | GuardrailSpec |
| `outputSchema { }` | OutputSchemaDelegate | OutputSchemaSpec |
| `search { }` | SearchDelegate | SearchSpec |
| `datasources { }` | DatasourcesBlockDelegate | DataSourceSpec |
| `autonomous { }` | AutonomousDelegate | AutonomousSpec |
| `browser_use { }` | BrowserUseDelegate | BrowserUseSpec |
| `workflow("name") { }` | (DslBaseScript 内) | WorkflowSpec |
| `steps { }` → `step { }` | StepsBlockDelegate → StepDelegate | StepSpec |
| `condition { }` | ConditionDelegate | (StepSpec.CONDITION) |
| `loop { }` | LoopDelegate | (StepSpec.LOOP) |

**DslBaseScript** 是所有 `.agent.groovy` 脚本的基类，提供顶层方法：

| 顶层方法 | 作用 |
|---------|------|
| `agent(name, closure)` | 定义 Agent |
| `tool(name, closure)` | 定义独立工具 |
| `workflow(name, closure)` | 定义工作流 |
| `skill(name, closure)` | 定义技能 |
| `datasource(name, closure)` | 定义数据源 |
| `env(key)` | 读取环境变量 |
| `file(path)` / `resource(path)` | 读取文件/资源内容 |

**扩展要点**:
- 新增 DSL 语法块 → 在 `dsl/` 下新建 Delegate（Groovy）+ 在 `spec/` 下新建 Spec（Java）
- 新增注解 → 在 `annotation/` 下定义
- 新增调试事件 → 在 `DebugEvent` 枚举中添加

---

### 5.2 agentdsl-compiler

> **定位**: DSL 编译器，将 `.agent.groovy` 文本解析为 Spec 对象图，并进行语义校验。

**源码路径**: `agentdsl-compiler/src/main/java/com/agentdsl/compiler/`

| 类 | 职责 |
|----|------|
| **DslCompiler** | 编译入口。使用 GroovyShell + DslBaseScript 解析脚本，支持沙箱模式（SecureASTCustomizer 限制危险 API）和超时保护 |
| **DslValidator** | 语义校验器（静态方法）。校验 Agent/Tool/Workflow/Skill 的必填项、引用存在性、参数约束 |
| **DslCompileResult** | 不可变编译结果，持有 agents/tools/workflows/skills/datasources/diagnostics |
| **Diagnostic** | 诊断信息（INFO/WARNING），不中断编译 |

**编译流程**:
```
DslCompiler.compile(scriptContent)
  1. CompilerConfiguration: 设置 DslBaseScript 基类 + import com.agentdsl.core.spec.*
  2. 沙箱模式: SecureASTCustomizer 禁止 System/Runtime/File/ProcessBuilder 等
  3. GroovyShell.parse() → Script 实例
  4. script.run() → DslBaseScript 内部收集 agents/tools/workflows/skills/datasources
  5. DslValidator.validateAll() → 产出 Diagnostic 列表
  6. 返回 DslCompileResult
```

**错误码约定**:
| 错误码 | 含义 |
|--------|------|
| ADSL-001 | 必填项缺失 |
| ADSL-002 | 编译/执行失败、参数不合法 |
| ADSL-003 | 引用不存在、执行超时 |
| ADSL-004 | Workflow 步骤执行模式错误 |

**扩展要点**:
- 新增校验规则 → 在 `DslValidator` 中添加对应 validate 方法
- 修改沙箱白名单/黑名单 → 修改 `DslCompiler.createSecureCustomizer()`

---

### 5.3 agentdsl-langchain4j

> **定位**: AgentDSL 与 LangChain4j 的桥接层，把声明式 Spec 转换为可执行的 LangChain4j 对象。

**源码路径**: `agentdsl-langchain4j/src/main/java/com/agentdsl/langchain4j/`

| 类 | 输入 → 输出 | 职责 |
|----|------------|------|
| **LangChainModelFactory** | ModelSpec → ChatModel | 根据 provider 创建对应 LLM 客户端 |
| **LangChainMemoryFactory** | MemorySpec → ChatMemory | 创建 message_window 或 token_window 记忆 |
| **LangChainRagFactory** | RagSpec → ContentRetriever | 创建嵌入模型 + 向量存储 + 检索器 |
| **LangChainToolBridge** | ToolSpec → ToolEntry(ToolSpecification, ToolExecutor) | 将 DSL 工具转为 LangChain4j 工具，支持闭包和 Bean 两种执行方式 |

**支持的 LLM Provider**:

| Provider 名称 | 实现方式 | 环境变量 |
|---------------|----------|----------|
| `openai` | OpenAiChatModel | `OPENAI_API_KEY` |
| `ollama` | OllamaChatModel | 默认 `http://localhost:11434` |
| `deepseek` | OpenAI 兼容 | `DEEPSEEK_API_KEY` |
| `kimi` / `moonshot` | OpenAI 兼容 | `MOONSHOT_API_KEY` |
| `doubao` | OpenAI 兼容 | `DOUBAO_API_KEY` |
| `qwen` | OpenAI 兼容 | `DASHSCOPE_API_KEY` |
| `zhipu` / `glm` | OpenAI 兼容 | `ZHIPU_API_KEY` |
| `minimax` | OpenAI 兼容 | `MINIMAX_API_KEY` |
| `claude` / `anthropic` | AnthropicChatModel | `ANTHROPIC_API_KEY` |
| `gemini` / `google` | GoogleAiGeminiChatModel | `GEMINI_API_KEY` |

**customSetting 参数支持差异**:

`ModelSpec.customSettings` 用于传递模型特定参数，不同 Provider 的支持情况：

| Provider | customSetting 支持参数 | 实现方式 |
|----------|------------------------|----------|
| OpenAI | 全部参数 | `customParameters(Map)` |
| Claude | 全部参数 | `customParameters(Map)` |
| DeepSeek/Kimi/Doubao/Qwen/Zhipu/Minimax | 全部参数 | OpenAI 兼容接口，通过`customParameters(Map)` |
| **Ollama** | `think`, `returnThinking`, `stop`, `minP`, `responseFormat` | 特定方法调用 |
| **Gemini** | `seed`, `frequencyPenalty`, `presencePenalty`, `returnThinking`, `sendThinking`, `stopSequences` | 特定方法调用 |

**源码位置**: `LangChainModelFactory.java` 中的 `createOllama()` 和 `createGemini()` 方法处理这些特定参数。

**LangChainToolBridge 工具执行机制**:
- **Closure 工具**: DSL 内联 `execute { }` 闭包，直接调用 Closure
- **Bean 工具**: `@AgentTool` 标注的 Java 方法，通过反射调用 `method.invoke(bean, args)`
- 执行前进行参数校验（required/pattern/min/max/enum）
- 执行后采集 MetricsCollector 指标

**扩展要点**:
- 新增 LLM Provider → 在 `LangChainModelFactory.create()` 的 switch 中添加分支
- 新增记忆类型 → 在 `LangChainMemoryFactory.create()` 中添加分支
- 新增嵌入模型/向量存储 → 扩展 `LangChainRagFactory`

---

### 5.4 agentdsl-tools

> **定位**: 内置工具库，提供开箱即用的常见工具能力。

**源码路径**: `agentdsl-tools/src/main/java/com/agentdsl/tools/`

| 类 | 职责 |
|----|------|
| **BuiltinToolRegistry** | 内置工具注册表，通过 ToolScanner 扫描所有内置工具并缓存 |
| **ToolScanner** | 扫描带 `@AgentTool` 注解的方法，生成 ToolSpec 列表 |

**内置工具清单** (`builtin/` 目录):

| 工具类 | 工具方法 | 功能 | 依赖 |
|--------|---------|------|------|
| **HttpTool** | `http_get`, `http_post` | HTTP 请求 | OkHttp3 |
| **JsonTool** | `json_parse`, `json_query` | JSON 解析与路径查询 | Gson |
| **FileTool** | `file_read`, `file_write` | 文件读写（白名单目录限制，默认 /tmp） | - |
| **ExcelTool** | `excel_read`, `excel_write` | Excel 读写 | Apache POI |
| **PdfTool** | `pdf_read` | PDF 文本提取 | Apache PDFBox |
| **ImageTool** | `image_recognize` | 图片识别（调用视觉模型 API） | OkHttp3 |
| **CmdTool** | `cmd_execute` | 本地命令执行（有命令黑名单） | - |
| **DatabaseTool** | `db_query`, `db_execute` | 数据库 SQL 执行 | JDBC + DataSourceRegistry |
| **WebSearchTool** | `web_search` | 网络搜索 | Tavily / Serper / 智谱 |
| **NativeBrowserTool** | `navigate`, `click`, `fill`, `getPageText` | Playwright 浏览器自动化 | Playwright |

> **注意**: NativeBrowserTool 不在 BuiltinToolRegistry 默认注册，仅在 Agent 配置了 `browser_use { }` 时由 AgentRegistry 动态加载。

**添加新内置工具的步骤**:

1. 在 `builtin/` 下新建 Java 类
2. 在方法上使用 `@AgentTool` 和 `@ToolParam` 注解
3. 在 `BuiltinToolRegistry.getBuiltinTools()` 中添加 `tools.addAll(ToolScanner.scan(new YourTool()))`
4. 如需新依赖，在 `agentdsl-tools/build.gradle.kts` 中添加

```java
public class MyTool {
    @AgentTool(name = "my_tool", description = "工具描述")
    public String myMethod(
        @ToolParam(name = "param1", description = "参数描述") String param1,
        @ToolParam(name = "param2", description = "可选参数", required = false) String param2) {
        return "result";
    }
}
```

---

### 5.5 agentdsl-mcp

> **定位**: MCP (Model Context Protocol) 桥接层，将外部 MCP Server 的工具映射为 LangChain4j 工具。

**源码路径**: `agentdsl-mcp/src/main/java/com/agentdsl/mcp/`

仅含一个核心类 **McpToolProviderBridge**：

| 方法 | 作用 |
|------|------|
| `connect(McpSpec, List<String> hitlActions)` | 根据配置建立 MCP 连接，返回工具列表 |
| `createTransport(McpServerSpec)` | 按 transport 类型创建 stdio / http / sse 传输层 |

**连接流程**:
```
connect(McpSpec)
  → 遍历 servers
  → createTransport(serverSpec) → StdioMcpTransport / HttpMcpTransport
  → DefaultMcpClient.Builder().transport(transport).build()
  → client.listTools() → List<ToolSpecification>
  → 按 filterToolNames 过滤
  → 为每个工具创建 ToolExecutor lambda (含 HITL 确认)
  → 返回 McpToolsResult { clients, toolSpecifications, toolExecutors }
```

**MCP 传输类型**:

| 传输方式 | 配置要求 | 典型用法 |
|---------|---------|---------|
| stdio | `command` 非空 | `npx -y @modelcontextprotocol/server-github` |
| http / sse | `url` 非空 | `http://localhost:3000/sse` |

**扩展要点**:
- 新增传输方式 → 在 `createTransport()` 中添加分支
- MCP 连接生命周期由 `AgentRegistry.closeMcpConnections()` 管理

---

### 5.6 agentdsl-runtime

> **定位**: 执行引擎，管理 Agent 生命周期、驱动对话循环、编排工作流、支持自主模式。

**源码路径**: `agentdsl-runtime/src/main/java/com/agentdsl/runtime/`

**核心类**:

| 类 | 职责 |
|----|------|
| **AgentDslEngine** | 引擎门面（Facade），统一入口：`load()` / `chat()` / `executeWorkflow()` / `executeAutonomous()` |
| **AgentRegistry** | 注册中心，管理 Agent、工具、Skill、MCP 连接、工作流的注册与查找 |
| **AgentInstance** | Agent 运行时实例，持有 ChatModel / ChatMemory / ToolSpecification / ToolExecutor / ContentRetriever |
| **AgentExecutor** | Agent 对话执行器，驱动 ReAct 工具调用循环（最多 10 轮） |
| **WorkflowExecutor** | 工作流执行器，支持顺序/并行/条件/循环步骤 |
| **WorkflowContext** | 工作流上下文，存放步骤间数据传递 |
| **WorkflowExecutionContext** | 步骤 `execute` 闭包的 delegate，提供 `toolCall()` 能力 |
| **WorkflowResult** | 工作流执行结果 |
| **HotReloader** | 热加载器，监听 `.agent.groovy` 文件变更并自动重载 |
| **McpDiscoveryService** | MCP 发现服务接口 |
| **SimpleMcpDiscoveryService** | 基于 MCP Registry API 的发现实现 |
| **SafetyGuard** | 安全守卫，敏感操作 HITL（Human-In-The-Loop）确认 |

**自主模式** (`autonomous/` 子包):

| 类 | 职责 |
|----|------|
| **AutonomousExecutor** | 自主执行器，支持 Plan / Fast 两种模式 |
| **PlannerEngine** | 规划引擎，用 LLM 生成/修改执行计划 |
| **ExecutionPlan** | 执行计划数据模型 |
| **UserInteraction** | 用户交互接口（抽象） |
| **ConsoleUserInteraction** | 控制台交互实现 |
| **AutonomousResult** | 自主执行结果 |
| **PlanFeedback** | 计划反馈（record） |

**指标追踪** (`metrics/` 子包):

| 类 | 职责 |
|----|------|
| **ExecutionTrace** | 工作流整体追踪 |
| **StepTrace** | 单步追踪 |

**AgentRegistry 注册流程详解**:
```
register(AgentSpec)
  1. LangChainModelFactory.create(modelSpec) → ChatModel
  2. LangChainMemoryFactory.create(memorySpec) → ChatMemory
  3. 收集工具:
     a. Agent 内联 tools
     b. BuiltinToolRegistry 内置工具
     c. Prompt Skill → 展平为 systemPrompt 片段
     d. Logic Skill → 展平为 ToolSpec
     e. McpToolProviderBridge.connect() → MCP 工具
     f. NativeBrowserTool (若配置 browser_use)
  4. LangChainToolBridge.convertAll(toolSpecs) → ToolEntry 列表
  5. LangChainRagFactory.create(ragSpec) → ContentRetriever (可选)
  6. 组装 AgentInstance 并存入 registry
```

**扩展要点**:
- 修改工具调用循环 → 修改 `AgentExecutor.execute()`
- 新增步骤执行类型 → 修改 `WorkflowExecutor`
- 自定义用户交互 → 实现 `UserInteraction` 接口
- 自定义 MCP 发现 → 实现 `McpDiscoveryService` 接口

---

### 5.7 agentdsl-cli

> **定位**: 命令行入口，提供 run / validate / list 三个子命令。

**源码路径**: `agentdsl-cli/src/main/java/com/agentdsl/cli/`

| 类 | 职责 |
|----|------|
| **AgentDslCli** | CLI 主入口，picocli @Command 注册子命令 |
| **RunCommand** | `agentdsl run <script>` — 执行 Agent 对话 / 工作流 / 自主模式 |
| **ValidateCommand** | `agentdsl validate <script>` — 校验 DSL 语法和语义 |
| **ListCommand** | `agentdsl list <script>` — 列出脚本中定义的 Agent/Tool/Workflow/Skill |
| **DebugTraceRenderer** | 将 DebugEvent 列表格式化为控制台树形输出 |

**CLI 命令选项速查**:

| 命令 | 主要选项 |
|------|---------|
| `run <script>` | `--chat/-c` 消息, `--agent/-a` 指定 Agent, `--workflow/-w` 工作流, `--input/-i` 输入, `--sandbox` 沙箱, `--debug/-d` 调试, `--trace` 追踪, `--autonomous/--auto` 自主模式 |
| `validate <script>` | `--json` JSON 输出, `--sandbox` 沙箱 |
| `list <script>` | `--format/-f text\|json` |

**打包**: 使用 Shadow 插件打 fat jar，主类 `com.agentdsl.cli.AgentDslCli`，输出 `agentdsl.jar`。

---

## 6. 核心设计模式

| 设计模式 | 应用位置 | 说明 |
|---------|---------|------|
| **闭包委托 (Delegate)** | agentdsl-core 所有 `*Delegate` | Groovy `Closure.DELEGATE_FIRST` 实现 DSL 语法解析 |
| **规范对象 (Spec)** | agentdsl-core `spec/` | DSL 解析后的不可变领域模型 |
| **工厂 (Factory)** | LangChainModelFactory, LangChainMemoryFactory, LangChainRagFactory | 根据 Spec 创建运行时对象 |
| **桥接 (Bridge)** | LangChainToolBridge, McpToolProviderBridge | 连接 DSL 领域与 LangChain4j / MCP 生态 |
| **门面 (Facade)** | AgentDslEngine | 统一的引擎入口，屏蔽内部复杂性 |
| **注册表 (Registry)** | AgentRegistry, BuiltinToolRegistry, DataSourceRegistry | 集中管理运行时对象 |
| **注解驱动** | @AgentTool, @ToolParam + ToolScanner | 声明式工具发现 |
| **命令 (Command)** | picocli RunCommand, ValidateCommand, ListCommand | CLI 子命令 |
| **策略 (Strategy)** | UserInteraction, McpDiscoveryService | 可替换的运行时行为 |
| **单例** | MetricsCollector, BuiltinToolRegistry（缓存） | 全局唯一实例 |
| **ThreadLocal** | DebugTracer | 线程隔离的调试追踪 |

---

## 7. 扩展开发指南

### 7.1 新增 LLM Provider

**涉及模块**: `agentdsl-langchain4j`

1. 在 `build.gradle.kts` 中添加 LangChain4j 的 provider 依赖
2. 在 `LangChainModelFactory.create()` 的 switch 中添加新 case
3. 实现 `createXxx(ModelSpec spec)` 私有方法
4. 在 `resolveEnv()` 中处理环境变量

### 7.2 新增内置工具

**涉及模块**: `agentdsl-tools`

1. 在 `builtin/` 下新建工具类
2. 用 `@AgentTool` 和 `@ToolParam` 标注方法
3. 在 `BuiltinToolRegistry.getBuiltinTools()` 中注册
4. 按需在 `build.gradle.kts` 中添加依赖

### 7.3 新增 DSL 语法块

**涉及模块**: `agentdsl-core` + `agentdsl-compiler`

1. 在 `core/spec/` 下新建 `XxxSpec.java`（Java POJO，getter/setter）
2. 在 `core/dsl/` 下新建 `XxxDelegate.groovy`（Groovy 闭包委托）
3. 在父级 Delegate（如 `AgentDelegate`）中添加 `xxx(Closure)` 方法
4. 在父级 Spec（如 `AgentSpec`）中添加对应字段
5. 在 `DslValidator` 中添加校验规则

### 7.4 新增工作流步骤执行类型

**涉及模块**: `agentdsl-core` + `agentdsl-runtime`

1. 在 `StepSpec` 中添加新字段
2. 在 `StepDelegate` 中添加解析方法
3. 在 `WorkflowExecutor` 的步骤执行分支中添加处理逻辑
4. 在 `DslValidator` 中更新互斥校验

### 7.5 新增 CLI 子命令

**涉及模块**: `agentdsl-cli`

1. 新建 `XxxCommand.java`，实现 `Callable<Integer>`，用 picocli `@Command` 注解
2. 在 `AgentDslCli` 的 `@Command(subcommands = ...)` 中注册

### 7.6 支持新的 MCP 传输方式

**涉及模块**: `agentdsl-mcp` + `agentdsl-core`

1. 在 `McpServerSpec` 中确保 transport 字段支持新类型
2. 在 `McpToolProviderBridge.createTransport()` 中添加新分支
3. 在 `McpServerDelegate` 中确保 DSL 语法支持

---

## 8. 项目目录快速索引

```
AgentDSL/
├── build.gradle.kts              # 根构建文件，定义通用依赖和插件
├── settings.gradle.kts           # 列出所有子模块
├── gradlew / gradlew.bat         # Gradle Wrapper
│
├── agentdsl-core/                # [核心] DSL 语法定义 + Spec 模型
│   └── src/main/
│       ├── groovy/.../dsl/       #   Delegate 类 (DSL 语法解析)
│       └── java/.../             #   annotation/, spec/, exception/, metrics/, utils/
│
├── agentdsl-compiler/            # [编译] DSL 编译器 + 语义校验
│   └── src/main/java/.../        #   DslCompiler, DslValidator, DslCompileResult, Diagnostic
│
├── agentdsl-langchain4j/         # [桥接] Spec → LangChain4j 对象
│   └── src/main/java/.../        #   LangChainModelFactory, MemoryFactory, RagFactory, ToolBridge
│
├── agentdsl-tools/               # [工具] 内置工具库
│   └── src/main/java/.../
│       ├── BuiltinToolRegistry   #   工具注册表
│       ├── ToolScanner           #   @AgentTool 注解扫描器
│       └── builtin/              #   HttpTool, JsonTool, FileTool, ExcelTool, PdfTool, ...
│
├── agentdsl-mcp/                 # [MCP] MCP 协议桥接
│   └── src/main/java/.../        #   McpToolProviderBridge
│
├── agentdsl-runtime/             # [运行时] 执行引擎
│   └── src/main/java/.../
│       ├── AgentDslEngine        #   引擎门面
│       ├── AgentRegistry         #   注册中心
│       ├── AgentInstance         #   Agent 实例
│       ├── AgentExecutor         #   对话执行器 (ReAct 循环)
│       ├── WorkflowExecutor      #   工作流执行器
│       ├── HotReloader           #   热加载
│       ├── SafetyGuard           #   HITL 安全确认
│       ├── McpDiscoveryService   #   MCP 发现接口
│       └── autonomous/           #   自主模式 (AutonomousExecutor, PlannerEngine, ...)
│
├── agentdsl-cli/                 # [CLI] 命令行入口
│   └── src/main/java/.../        #   AgentDslCli, RunCommand, ValidateCommand, ListCommand
│
├── examples/                     # 示例脚本
│   ├── simple-chat.agent.groovy
│   ├── tool-agent.agent.groovy
│   ├── workflow-pipeline.agent.groovy
│   ├── mcp-github.agent.groovy
│   ├── autonomous-agent.agent.groovy
│   └── ... (19 个示例)
│
├── doc/                          # 文档
│   ├── lang-spec/                #   语言规范 (v1.0 ~ v1.4)
│   ├── arch/                     #   架构决策文档
│   ├── 需求/                     #   需求文档
│   └── 设计/                     #   设计文档
│
├── shell/                        # 构建和运行脚本
├── docker/                       # Dockerfile + docker-compose
└── skills/                       # Skill 文件示例
```

---

## 9. 常见开发场景速查

| 场景 | 需要修改的模块和文件 |
|------|---------------------|
| **添加新的 LLM Provider** | `agentdsl-langchain4j/LangChainModelFactory.java` |
| **添加新的内置工具** | `agentdsl-tools/builtin/` 新建工具类 + `BuiltinToolRegistry` 注册 |
| **添加新的 DSL 关键字** | `agentdsl-core/dsl/` 新建 Delegate + `spec/` 新建 Spec + 父级 Delegate/Spec 接入 |
| **修改 Agent 对话循环逻辑** | `agentdsl-runtime/AgentExecutor.java` |
| **修改工作流执行逻辑** | `agentdsl-runtime/WorkflowExecutor.java` |
| **添加新的校验规则** | `agentdsl-compiler/DslValidator.java` |
| **修改沙箱安全策略** | `agentdsl-compiler/DslCompiler.java` → `createSecureCustomizer()` |
| **添加新的 CLI 命令** | `agentdsl-cli/` 新建 Command 类 + `AgentDslCli` 注册 |
| **添加新的 MCP 传输方式** | `agentdsl-mcp/McpToolProviderBridge.java` → `createTransport()` |
| **修改自主模式逻辑** | `agentdsl-runtime/autonomous/AutonomousExecutor.java` / `PlannerEngine.java` |
| **修改 MCP 自动发现** | `agentdsl-runtime/SimpleMcpDiscoveryService.java` 或实现新的 `McpDiscoveryService` |
| **添加新的调试事件类型** | `agentdsl-core/metrics/DebugEvent.java` + `agentdsl-cli/DebugTraceRenderer.java` |
| **修改热加载行为** | `agentdsl-runtime/HotReloader.java` |
| **添加新的搜索 Provider** | `agentdsl-tools/builtin/WebSearchTool.java` |
| **添加新的记忆类型** | `agentdsl-langchain4j/LangChainMemoryFactory.java` |
| **修改工具参数校验** | `agentdsl-langchain4j/LangChainToolBridge.java` → `validateParameters()` |

---

## 附录 A: 构建与运行

```bash
# 构建全部模块
./gradlew build

# 运行 CLI
./gradlew :agentdsl-cli:run --args="run examples/simple-chat.agent.groovy --chat '你好'"

# 校验脚本
./gradlew :agentdsl-cli:run --args="validate examples/simple-chat.agent.groovy"

# 列出脚本定义
./gradlew :agentdsl-cli:run --args="list examples/simple-chat.agent.groovy --format json"

# 打包 fat jar
./gradlew :agentdsl-cli:shadowJar
java -jar agentdsl-cli/build/libs/agentdsl.jar run script.agent.groovy --chat "hello"

# 运行测试
./gradlew test
```

## 附录 B: 关键环境变量

| 环境变量 | 用途 |
|---------|------|
| `OPENAI_API_KEY` | OpenAI 模型 |
| `GEMINI_API_KEY` | Google Gemini 模型 |
| `ANTHROPIC_API_KEY` | Claude 模型 |
| `DEEPSEEK_API_KEY` | DeepSeek 模型 |
| `MOONSHOT_API_KEY` | Kimi / Moonshot 模型 |
| `DOUBAO_API_KEY` | 豆包模型 |
| `DASHSCOPE_API_KEY` | 通义千问模型 |
| `ZHIPU_API_KEY` | 智谱 GLM 模型 |
| `MINIMAX_API_KEY` | MiniMax 模型 |
| `TAVILY_API_KEY` | Tavily 搜索 |
| `SERPER_API_KEY` | Serper 搜索 |
| `GITHUB_TOKEN` | GitHub MCP Server |
