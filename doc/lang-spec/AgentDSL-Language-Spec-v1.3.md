# AgentDSL 语言定义规范 v1.4.0

> 本文档是 AgentDSL 的正式语言规范，定义了 DSL 的语法结构、关键字、类型系统和语义规则。
> 所有 DSL 引擎的实现必须遵循本规范。

> [!NOTE]
> v1.1 相对 v1.0 的主要变更：
> - 重新设计工作流语法（`steps`/`parallel`/`condition` 替代 `flow`/`start`/`node`）
> - 新增 `@AgentTool`/`@ToolParam` 注解工具发现机制
> - 新增热加载语义
> - 新增安全沙箱规则（超时保护、AST 黑名单）
> - 更新 Provider 表和 LangChain4j API 映射

> v1.2 相对 v1.1 的主要变更：
> - **核心重构**：ToolSpec 工具定义深度增强（新增 `returns`、`timeout`、`onError`、`permissions` 及 Parameter 的高级校验如 `pattern`、`min`、`max`、`enumValues`）。
> - **内置能力**：引入 `BuiltinToolRegistry`，内置 `http_get`, `http_post`, `json_parse`, `json_query`, `file_read`, `file_write` 工具。
> - **生态接入**：新增 `mcp` 协议支持，允许 Agent 无缝连接基于 Model Context Protocol 的工具服务器（支持 stdio/http）。
> - **知识管理**：新增 `skill` 技能体系支持，支持定义和加载 `PROMPT` (提示词) 和 `LOGIC` (代码逻辑) 技能，支持 `.skill.md` 外部文件 `includeFile` 集成。
> - **新提供商**：支持 Gemini API。

> [!NOTE]
> v1.3.0 相对 v1.2 的主要变更：
> - **数据基础设施**：顶层增加 `datasource` 定义，Agent 内增加 `datasources` 引用，支持跨生态的 JDBC 数据库访问。
> - **内置工具集扩充**：新增 `excel_read`, `excel_write`, `pdf_read`, `image_recognize`, `cmd_execute`, `db_query`, `db_execute`, `web_search` 内置能力。
> - **原生浏览器集成**：新增 `browser_use` 关键字，直接驱动浏览器操作。
> - **增强搜索能力**：新增 `search` 配置块，支持 `tavily`, `serper`, `zhipu` 等主流搜索 Provider。
> - **调试追踪系统**：CLI 支持 `--debug` 全链路追踪输出。

> [!NOTE]
> v1.4.0 相对 v1.3.0 的主要变更：
> - **自主 Agent**：新增 `autonomous` 配置块，支持 `plan`（规划确认后执行）和 `fast`（直接执行）两种自主模式，内置 ReAct 循环引擎（PlannerEngine + AutonomousExecutor）、`max_steps` 熔断机制及 Human-In-The-Loop 交互接口。
> - **Workflow 直接执行**：Step 新增四种无 LLM 执行模式（`execute` / `tool` / `skill` / `mcp`），与现有 `agent` 认知节点平级并存，彻底消除"LLM Tax"。引入 `WorkflowExecutionContext` 上下文对象，支持在纯代码步骤中通过 `ctx.toolCall()` 直接调用注册工具。
> - **新调试事件**：`DebugEvent` 新增 `CODE_EXECUTE`、`DIRECT_TOOL_CALL`、`DIRECT_SKILL_CALL`、`DIRECT_MCP_CALL` 四类追踪事件。

---

## 1. 概述

### 1.1 设计原则

| 原则             | 说明                                                |
| ---------------- | --------------------------------------------------- |
| **声明式优先**   | 用户描述 Agent "是什么"，而非"怎么做"               |
| **约定优于配置** | 合理的默认值，最小化必填项                          |
| **类型安全**     | 编译期校验关键配置，减少运行时错误                  |
| **可组合**       | 所有构件（Agent、Tool、Memory）可独立定义并自由组合 |
| **渐进式复杂度** | 简单场景 10 行可用，复杂场景按需扩展                |

### 1.2 文件约定

| 项目       | 约定                                                       |
| ---------- | ---------------------------------------------------------- |
| 文件扩展名 | `.agent.groovy`                                            |
| 文件编码   | UTF-8                                                      |
| 顶层声明   | 每个文件允许多个顶层声明（`agent`、`tool`、`workflow` 等） |

---

## 2. 词法结构

### 2.1 关键字（Keywords）

AgentDSL 的关键字分为以下层级：

#### 顶层关键字（Top-Level Keywords）

定义独立的 DSL 构件，只能出现在脚本最外层。

| 关键字       | 描述                        | 必填参数       |
| ------------ | --------------------------- | -------------- |
| `agent`      | 定义一个 Agent              | `name: String` |
| `tool`       | 定义一个可复用工具          | `name: String` |
| `workflow`   | 定义一个多 Agent 工作流     | `name: String` |
| `skill`      | 定义一个可复用技能 (v1.2)   | `name: String` |
| `datasource` | 定义全局 JDBC 数据源 (v1.3) | `name: String` |

#### Agent 块关键字（Agent Block Keywords）

仅可出现在 `agent { }` 闭包内部。

| 关键字         | 描述                        | 类型     | 必填   |
| -------------- | --------------------------- | -------- | ------ |
| `description`  | Agent 的用途描述            | `String` | 否     |
| `model`        | LLM 模型配置块              | `Block`  | **是** |
| `systemPrompt` | 系统提示词                  | `String` | 否     |
| `memory`       | 记忆配置块                  | `Block`  | 否     |
| `tools`        | 工具集配置块                | `Block`  | 否     |
| `skills`       | 技能集配置块 (v1.2)         | `Block`  | 否     |
| `mcp`          | MCP 服务配置块 (v1.2)       | `Block`  | 否     |
| `browser_use`  | 原生浏览器块配置 (v1.3)     | `Block`  | 否     |
| `datasources`  | 数据源引用配置 (v1.3)       | `Block`  | 否     |
| `search`       | 搜索引擎全局配置 (v1.3)     | `Block`  | 否     |
| `rag`          | RAG 检索增强配置块          | `Block`  | 否     |
| `guardrails`   | 安全护栏配置块              | `Block`  | 否     |
| `outputSchema` | 结构化输出定义块            | `Block`  | 否     |
| `autonomous`   | 自主执行模式配置块 (v1.4.0) | `Block`  | 否     |

#### Model 块关键字

仅可出现在 `model { }` 闭包内部。

| 关键字        | 描述                         | 类型      | 默认值                  |
| ------------- | ---------------------------- | --------- | ----------------------- |
| `provider`    | 模型提供商标识               | `String`  | 必填                    |
| `modelName`   | 模型名称                     | `String`  | 必填                    |
| `apiKey`      | API 密钥（支持环境变量引用） | `String`  | `env("OPENAI_API_KEY")` |
| `baseUrl`     | 自定义 API 端点              | `String`  | 按 provider 默认        |
| `temperature` | 温度参数                     | `Double`  | `0.7`                   |
| `topP`        | Top-P 采样                   | `Double`  | `1.0`                   |
| `maxTokens`   | 最大输出 token 数            | `Integer` | `2048`                  |
| `timeout`     | 请求超时（秒）               | `Integer` | `60`                    |

#### Memory 块关键字

| 关键字        | 描述              | 类型         | 默认值             |
| ------------- | ----------------- | ------------ | ------------------ |
| `type`        | 记忆策略类型      | `MemoryType` | `"message_window"` |
| `maxMessages` | 最大保留消息数    | `Integer`    | `20`               |
| `maxTokens`   | 最大保留 token 数 | `Integer`    | -                  |

`MemoryType` 枚举值：

| 值                 | 对应 LangChain4j 类       |
| ------------------ | ------------------------- |
| `"message_window"` | `MessageWindowChatMemory` |
| `"token_window"`   | `TokenWindowChatMemory`   |

#### Tools 块关键字 (v1.2增强)

| 关键字    | 描述                         | 用法                   |
| --------- | ---------------------------- | ---------------------- |
| `include` | 引用已注册的工具 或 内置工具 | `include "toolName"`   |
| `tool`    | 内联定义一个工具             | `tool("name") { ... }` |

> v1.3.0 系统内置工具预注册名已扩充覆盖全系能力：`http_get`, `http_post`, `json_parse`, `json_query`, `file_read`, `file_write`, `excel_read`, `excel_write`, `pdf_read`, `image_recognize`, `cmd_execute`, `db_query`, `db_execute`, `web_search`。均可直接使用 `include` 引入。

#### Tool 定义关键字（`tool { }` 内部，v1.2增强）

| 关键字        | 描述                        | 类型             | 必填        |
| ------------- | --------------------------- | ---------------- | ----------- |
| `description` | 工具描述（LLM 可见）        | `String`         | **是**      |
| `returns`     | 返回值类型与描述声明        | `String, String` | 否          |
| `timeout`     | 执行超时控制 (秒)           | `Integer`        | 否 (默认30) |
| `permissions` | 权限声明块                  | `Block`          | 否          |
| `parameter`   | 定义一个输入参数            | `Block`          | 否          |
| `onError`     | 异常处理闭包                | `Closure`        | 否          |
| `execute`     | 工具执行逻辑（Groovy 闭包） | `Closure`        | **是**      |

#### Permissions 定义关键字 (`permissions { }` 内部)
| 关键字    | 描述                       | 示例                                  |
| --------- | -------------------------- | ------------------------------------- |
| `network` | 允许访问的网络地址(通配符) | `network "https://api.example.com/*"` |
| `file`    | 允许访问的本地路径         | `file "/tmp/workspace/*"`             |

#### Parameter 定义关键字 (v1.2增强)

| 关键字         | 描述         | 类型                |
| -------------- | ------------ | ------------------- |
| `name`         | 参数名       | `String`            |
| `type`         | 参数类型     | `ParamType`         |
| `description`  | 参数描述     | `String`            |
| `required`     | 是否必填     | `Boolean`           |
| `pattern`      | 正则验证     | `String`            |
| `defaultValue` | 默认值       | `Object`            |
| `enumValues`   | 允许的枚举值 | `String` (逗号分隔) |
| `min` / `max`  | 数值范围限制 | `Double`            |

`ParamType` 枚举值：`"string"`, `"integer"`, `"double"`, `"boolean"`, `"list"`, `"map"`

#### Skills / Skill 块关键字 (v1.2 新增)

`skills { }` 用于 Agent 引用技能：
| 关键字        | 描述                          | 用法                                  |
| ------------- | ----------------------------- | ------------------------------------- |
| `include`     | 引用全局注册的技能            | `include "skillName"`                 |
| `includeFile` | 加载外部 `.skill.md` 格式文件 | `includeFile "skills/brand.skill.md"` |

`skill("name") { }` (顶层或内联) 用于定义技能：
| 关键字        | 描述                           | 类型      | 必填            |
| ------------- | ------------------------------ | --------- | --------------- |
| `type`        | 技能类型 (`PROMPT` 或 `LOGIC`) | `String`  | 否 (默认PROMPT) |
| `description` | 技能描述                       | `String`  | **是**          |
| `instruction` | 提示词指令内容(仅 PROMPT)      | `String`  | 否              |
| `executeBody` | 执行逻辑(仅 LOGIC)             | `Closure` | 否              |
| `parameter`   | 参数定义(同 tool parameter)    | `Block`   | 否              |

#### MCP 块关键字 (v1.2 新增)

`mcp { }` 用于定义 Agent 可使用的 Model Context Protocol Servers。

| 关键字   | 描述                    | 类型              | 必填   |
| -------- | ----------------------- | ----------------- | ------ |
| `server` | 定义一个 MCP 服务器连接 | `Block` (含 name) | **是** |

`server("name") { }` 内部：
| 关键字        | 描述                               | 类型        | 必填      |
| ------------- | ---------------------------------- | ----------- | --------- |
| `transport`   | 传输类型 (`stdio` 或 `http` 等)    | `String`    | **是**    |
| `command`     | 进程启动命令(stdio)                | `String...` | stdio必填 |
| `env`         | 环境变量(stdio)                    | `K, V`      | 否        |
| `url`         | 连接地址(http sse)                 | `String`    | http必填  |
| `filterTools` | 仅暴露该 MCP Server 中列出的工具名 | `String...` | 否        |

#### BrowserUse 块关键字 (v1.3 新增)

`browser_use { }` 用于开启 Agent 的原生浏览器环境智能驱动。底层无需配置外部 MCP Server。

| 关键字    | 描述                                                           | 类型        | 必填           |
| --------- | -------------------------------------------------------------- | ----------- | -------------- |
| `sandbox` | 是否开启无头模式可视化隔离保护                                 | `Boolean`   | 否 (默认false) |
| `hitl_on` | 请求 "Human In The Loop" (人工授权) 的动作如 `"click", "fill"` | `String...` | 否             |

#### DataSource 块关键字 (v1.3 新增)

顶层声明 `datasource("name") { }` 用于配置 JDBC 连接，Agent 内部用 `datasources { attach "name" }` 挂载使用。

| 关键字           | 描述                      | 类型      | 必填        |
| ---------------- | ------------------------- | --------- | ----------- |
| `type`           | 数据源类型 (如 h2, mysql) | `String`  | **是**      |
| `url`            | JDBC URL连接串            | `String`  | **是**      |
| `username`       | 用户名                    | `String`  | 否          |
| `password`       | 密码                      | `String`  | 否          |
| `maxConnections` | 最大连接数                | `Integer` | 否 (默认10) |

#### Search 块关键字 (v1.3 新增)

`search { }` 用于配置 Agent 使用的底层搜索引擎。

| 关键字       | 描述                                  | 类型      | 必填   |
| ------------ | ------------------------------------- | --------- | ------ |
| `provider`   | 搜索提供商 (如 tavily, serper, zhipu) | `String`  | **是** |
| `apiKey`     | API 密钥                              | `String`  | **是** |
| `maxResults` | 返回的最大结果数 (由底层支持)         | `Integer` | 否     |


#### RAG 块关键字

| 关键字             | 描述           | 类型    |
| ------------------ | -------------- | ------- |
| `contentRetriever` | 内容检索器配置 | `Block` |

#### ContentRetriever 块关键字

| 关键字           | 描述           | 类型      | 默认值              |
| ---------------- | -------------- | --------- | ------------------- |
| `type`           | 检索器类型     | `String`  | `"embedding_store"` |
| `embeddingModel` | 嵌入模型名称   | `String`  | 必填                |
| `maxResults`     | 最大检索结果数 | `Integer` | `5`                 |
| `minScore`       | 最低相关性分数 | `Double`  | `0.0`               |

#### Guardrails 块关键字

| 关键字                | 描述                  | 类型                   |
| --------------------- | --------------------- | ---------------------- |
| `maxTokensPerRequest` | 单次请求最大 token 数 | `Integer`              |
| `blockedTopics`       | 封禁话题列表          | `String...` (可变参数) |
| `inputValidator`      | 自定义输入校验器      | `Closure<Boolean>`     |
| `outputValidator`     | 自定义输出校验器      | `Closure<Boolean>`     |

#### OutputSchema 块关键字

| 关键字  | 描述             | 类型                                              |
| ------- | ---------------- | ------------------------------------------------- |
| `field` | 定义一个输出字段 | `name: String, type: String, description: String` |

#### Workflow 块关键字（v1.1 新增）

仅可出现在 `workflow { }` 闭包内部。

| 关键字        | 描述       | 类型     | 必填   |
| ------------- | ---------- | -------- | ------ |
| `description` | 工作流描述 | `String` | 否     |
| `steps`       | 步骤定义块 | `Block`  | **是** |

#### Steps 块关键字（v1.1 新增）

| 关键字      | 描述                 | 用法                                 |
| ----------- | -------------------- | ------------------------------------ |
| `step`      | 定义一个顺序执行步骤 | `step("name") { ... }`               |
| `parallel`  | 定义一组并行执行步骤 | `parallel { step(...) ... }`         |
| `condition` | 定义一个条件分支     | `condition { check { ... } on ... }` |
| `loop`      | 定义一个循环迭代步骤 | `loop(maxIterations: N) { ... }`     |

#### Step 定义关键字（v1.1 新增）

| 关键字   | 描述                        | 类型      | 必填   |
| -------- | --------------------------- | --------- | ------ |
| `agent`  | 引用执行该步骤的 Agent 名称 | `String`  | **是** |
| `input`  | 输入转换逻辑                | `Closure` | 否     |
| `output` | 输出转换逻辑                | `Closure` | 否     |

#### Condition 定义关键字（v1.1 新增）

| 关键字  | 描述                 | 类型      | 必填   |
| ------- | -------------------- | --------- | ------ |
| `check` | 条件判断逻辑         | `Closure` | **是** |
| `on`    | 条件值对应的步骤分支 | `Block`   | **是** |

#### Loop 定义关键字（v1.1 新增）

| 关键字          | 描述             | 类型      | 必填           |
| --------------- | ---------------- | --------- | -------------- |
| `maxIterations` | 最大循环次数     | `Integer` | **是**（参数） |
| `until`         | 循环终止条件闭包 | `Closure` | **是**         |

#### Step 定义关键字（v1.4.0 扩展）

`step("name") { }` 内部支持五种**互斥**执行模式，必须且只能指定其中一种：

| 关键字    | 执行模式     | 描述                                                         | LLM 参与 | 类型      |
| --------- | ------------ | ------------------------------------------------------------ | -------- | --------- |
| `agent`   | 认知节点     | 引用已定义的 Agent，由大模型推理执行（原有行为）             | ✅ 是     | `String`  |
| `execute` | 纯代码节点   | 直接执行 Groovy 闭包，闭包参数为 `WorkflowExecutionContext`  | ❌ 否     | `Closure` |
| `tool`    | 工具调用节点 | 直接调用已注册工具（全局 tool 或内置工具），绕过 LLM         | ❌ 否     | `String`  |
| `skill`   | 技能调用节点 | 直接调用已注册 Logic Skill，绕过 LLM                         | ❌ 否     | `String`  |
| `mcp`     | MCP 调用节点 | 直接调用指定 MCP 服务器上的工具，绕过 LLM；参数为 `(serverName, toolName)` | ❌ 否     | `String, String` |

`input` / `output` 闭包在所有模式下均可使用，语义如下：

| 闭包     | `agent` 模式返回类型 | `tool`/`skill`/`mcp` 模式返回类型 | `execute` 模式 |
| -------- | -------------------- | --------------------------------- | -------------- |
| `input`  | `String`（聊天消息） | `Map<String, Object>`（工具参数） | 不适用（通过 `ctx` 直接访问） |
| `output` | `Object`（后处理）   | `Object`（后处理）                | `Object`（后处理） |

#### Autonomous 块关键字（v1.4.0 新增）

`autonomous { }` 用于开启 Agent 的自主执行能力（ReAct 循环）。

| 关键字           | 描述                                     | 类型      | 默认值  |
| ---------------- | ---------------------------------------- | --------- | ------- |
| `execution_mode` | 执行模式：`"plan"` 或 `"fast"`           | `String`  | 必填    |
| `max_steps`      | 最大自主执行步数，超出后暂停询问用户     | `Integer` | `10`    |

**执行模式说明：**

| 模式     | 描述                                                         |
| -------- | ------------------------------------------------------------ |
| `"plan"` | 规划模式：先生成完整执行计划，用户确认后再执行。适合重要/风险操作。 |
| `"fast"` | 快速模式：跳过规划阶段，直接开始执行。适合简单/低风险任务。   |

#### WorkflowExecutionContext 上下文（v1.4.0 新增）

在 `execute` 闭包中，系统自动注入 `WorkflowExecutionContext` 对象作为闭包的 delegate 和参数，提供以下能力：

| 方法/属性                                   | 描述                                             |
| ------------------------------------------- | ------------------------------------------------ |
| `ctx.lastOutput`                            | 获取上一个步骤的输出（`Object`）                 |
| `ctx.getStepResult(String stepName)`        | 按名称获取任意已执行步骤的输出                   |
| `ctx.getAllStepResults()`                   | 获取所有步骤结果的只读 `Map`                     |
| `ctx.initialInput`                          | 获取 Workflow 的原始输入字符串                   |
| `ctx.toolCall(String name, Map params)`     | 直接调用已注册工具（全局 tool 或内置工具）        |

---

## 3. 语法规则（Grammar）

以下使用 EBNF 风格描述 AgentDSL 的语法结构：

```ebnf
(* === 顶层 === *)
script          ::= { topLevelDecl } ;
topLevelDecl    ::= agentDecl | toolDecl | workflowDecl | skillDecl | datasourceDecl ;

(* === Agent 声明 === *)
agentDecl       ::= "agent" "(" STRING ")" "{" agentBody "}" ;
agentBody       ::= { agentProperty } ;
agentProperty   ::= descriptionProp
                   | modelBlock
                   | systemPromptProp
                   | memoryBlock
                   | toolsBlock
                   | skillsBlock
                   | mcpBlock
                   | browserUseBlock
                   | datasourcesBlock
                   | ragBlock
                   | guardrailsBlock
                   | outputSchemaBlock
                   | autonomousBlock ;

(* === 简单属性 === *)
descriptionProp   ::= "description" STRING ;
systemPromptProp  ::= "systemPrompt" ( STRING | MULTILINE_STRING ) ;

(* === Model 块 === *)
modelBlock      ::= "model" "{" { modelProp } "}" ;
modelProp       ::= "provider" STRING
                   | "modelName" STRING
                   | "apiKey" ( STRING | envRef )
                   | "baseUrl" STRING
                   | "temperature" DOUBLE
                   | "topP" DOUBLE
                   | "maxTokens" INTEGER
                   | "timeout" INTEGER ;
envRef          ::= "env" "(" STRING ")" ;

(* === Memory 块 === *)
memoryBlock     ::= "memory" "{" { memoryProp } "}" ;
memoryProp      ::= "type" STRING
                   | "maxMessages" INTEGER
                   | "maxTokens" INTEGER ;

(* === Tools 块 === *)
toolsBlock      ::= "tools" "{" { toolEntry } "}" ;
toolEntry       ::= "include" STRING
                   | toolDecl ;

(* === Tool 声明 === *)
toolDecl        ::= "tool" "(" STRING ")" "{" toolBody "}" ;
toolBody        ::= { toolProp } ;
toolProp        ::= "description" STRING
                   | "returns" STRING "," STRING
                   | "timeout" INTEGER
                   | "permissions" "{" { permissionProp } "}"
                   | parameterDecl
                   | "onError" CLOSURE
                   | "execute" CLOSURE ;
permissionProp  ::= ( "network" | "file" ) STRING ;
parameterDecl   ::= "parameter" "{" { paramProp } "}" ;
paramProp       ::= "name" STRING | "type" STRING | "description" STRING 
                   | "required" BOOLEAN | "pattern" STRING 
                   | "defaultValue" ANY | "enumValues" STRING 
                   | "min" DOUBLE | "max" DOUBLE ;

(* === MCP 块 === *)
mcpBlock        ::= "mcp" "{" { mcpServerDecl } "}" ;
mcpServerDecl   ::= "server" "(" STRING ")" "{" serverProp "}" ;
serverProp      ::= "transport" STRING
                   | "command" STRING { "," STRING }
                   | "env" STRING "," STRING
                   | "url" STRING
                   | "filterTools" STRING { "," STRING } ;

(* === Skill 块 === *)
skillsBlock     ::= "skills" "{" { skillEntry } "}" ;
skillEntry      ::= "include" STRING | "includeFile" STRING ;
skillDecl       ::= "skill" "(" STRING ")" "{" skillBody "}" ;
skillBody       ::= "type" STRING | "description" STRING 
                   | "instruction" MULTILINE_STRING
                   | parameterDecl | "executeBody" CLOSURE ;

(* === DataSource 块 (v1.3) === *)
datasourceDecl  ::= "datasource" "(" STRING ")" "{" datasourceProp "}" ;
datasourceProp  ::= "type" STRING | "url" STRING | "username" STRING | "password" STRING | "maxConnections" INTEGER ;

datasourcesBlock ::= "datasources" "{" { "attach" STRING } "}" ;

(* === BrowserUse 块 (v1.3) === *)
browserUseBlock  ::= "browser_use" "{" { browserUseProp } "}" ;
browserUseProp   ::= "sandbox" BOOLEAN | "hitl_on" STRING { "," STRING } ;

(* === Search 块 (v1.3) === *)
searchBlock      ::= "search" "{" { searchProp } "}" ;
searchProp       ::= "provider" STRING | "apiKey" ( STRING | envRef ) | "maxResults" INTEGER ;


(* === RAG 块 === *)
ragBlock        ::= "rag" "{" retrieverDecl "}" ;
retrieverDecl   ::= "contentRetriever" "{" { retrieverProp } "}" ;
retrieverProp   ::= "type" STRING
                   | "embeddingModel" STRING
                   | "maxResults" INTEGER
                   | "minScore" DOUBLE ;

(* === Guardrails 块 === *)
guardrailsBlock   ::= "guardrails" "{" { guardrailProp } "}" ;
guardrailProp     ::= "maxTokensPerRequest" INTEGER
                     | "blockedTopics" STRING { "," STRING }
                     | "inputValidator" CLOSURE
                     | "outputValidator" CLOSURE ;

(* === OutputSchema 块 === *)
outputSchemaBlock ::= "outputSchema" "{" { fieldDecl } "}" ;
fieldDecl         ::= "field" STRING "," STRING "," STRING ;

(* === Workflow 声明 (v1.1) === *)
workflowDecl    ::= "workflow" "(" STRING ")" "{" workflowBody "}" ;
workflowBody    ::= { workflowProp } ;
workflowProp    ::= descriptionProp
                   | stepsBlock ;

stepsBlock      ::= "steps" "{" { stepEntry } "}" ;
stepEntry       ::= sequentialStep | parallelStep | conditionStep | loopStep ;

sequentialStep  ::= "step" "(" STRING ")" "{" stepBody "}" ;
(* stepBody 中执行模式五选一（必填），input/output 可选 *)
stepBody        ::= executionMode [ "input" CLOSURE ] [ "output" CLOSURE ] ;
executionMode   ::= "agent" STRING
                   | "execute" CLOSURE
                   | "tool" STRING
                   | "skill" STRING
                   | "mcp" STRING "," STRING ;

parallelStep    ::= "parallel" "{" { sequentialStep } "}" ;

conditionStep   ::= "condition" "{" conditionBody "}" ;
conditionBody   ::= "check" CLOSURE { conditionBranch } ;
conditionBranch ::= "on" STRING "{" { stepEntry } "}" ;

loopStep        ::= "loop" "(" "maxIterations" ":" INTEGER ")" "{" { stepEntry } untilClause { stepEntry } "}" ;
untilClause     ::= "until" CLOSURE ;

(* === Autonomous 块 (v1.4.0) === *)
autonomousBlock  ::= "autonomous" "{" { autonomousProp } "}" ;
autonomousProp   ::= "execution_mode" STRING | "max_steps" INTEGER ;

(* === 基础类型 === *)
STRING           ::= '"' { character } '"' ;
MULTILINE_STRING ::= '"""' { character | newline } '"""' ;
INTEGER          ::= digit { digit } ;
DOUBLE           ::= digit { digit } "." digit { digit } ;
BOOLEAN          ::= "true" | "false" ;
CLOSURE          ::= "{" groovy_code "}" ;
```

---

## 4. 语义规则

### 4.1 名称唯一性

- 同一脚本内，所有 `agent`、`tool`、`workflow` 的 `name` 必须唯一
- 跨文件的 `name` 在注册中心中必须全局唯一，后注册的将覆盖先注册的（并产生警告日志）

### 4.2 必填校验

编译器在解析完 DSL 脚本后，必须校验以下必填规则：

| 构件       | 必填项                                                             |
| ---------- | ------------------------------------------------------------------ |
| `agent`    | `name`, `model.provider`, `model.modelName`                        |
| `tool`     | `name`, `description`, `execute`                                   |
| `workflow` | `name`, `steps`（至少 1 个 step）                                  |
| `step`     | `name`, 执行模式（`agent`/`execute`/`tool`/`skill`/`mcp` 五选一） |

违反必填规则时，编译器应抛出 `DslCompilationException`，包含清晰的错误位置和缺失字段名。

**Step 执行模式互斥规则：**

- SEQUENTIAL 类型的 step 必须且只能指定一种执行模式，违反时抛出错误码 `ADSL-004`
- `mcp` 模式必须同时提供服务器名称和工具名称两个参数
- 执行模式互斥校验递归应用于 `parallel`、`condition` 分支和 `loop` 体内的所有 step

### 4.3 工具引用解析

`tools { include "toolName" }` 的解析规则：

1. 首先在当前脚本中查找同名 `tool` 定义
2. 其次在 `AgentRegistry` 中查找已注册的全局工具
3. 最后在 classpath 中查找带有 `@AgentTool` 注解的方法
4. 若未找到，抛出 `DslRuntimeException`（错误码 `ADSL-010`）

### 4.4 环境变量引用

`env("VAR_NAME")` 函数在运行时解析，规则如下：

1. 优先读取 JVM 系统属性 `System.getProperty("VAR_NAME")`
2. 其次读取环境变量 `System.getenv("VAR_NAME")`
3. 若均未找到，抛出 `DslRuntimeException`（错误码 `ADSL-012`）

### 4.5 闭包作用域

- `execute` 闭包内可访问工具参数（通过 `params` 隐式变量）
- `execute` 闭包内禁止访问 Agent 的上下文（隔离原则）
- `inputValidator` / `outputValidator` 闭包接收 `String` 参数，返回 `Boolean`

### 4.6 工具注解发现（v1.1 新增）

除 DSL 脚本中的 `tool()` 声明外，系统支持通过 Java 注解自动发现工具：

```java
public class MyTools {
    @AgentTool(name = "getWeather", description = "查询天气")
    public String weather(@ToolParam(description = "城市名称") String city) {
        return city + ": 晴天 25°C";
    }
}
```

**扫描规则：**

| 注解         | 目标     | 属性                                     |
| ------------ | -------- | ---------------------------------------- |
| `@AgentTool` | 方法     | `name`（默认取方法名）, `description`    |
| `@ToolParam` | 方法参数 | `description`, `required`（默认 `true`） |

**类型映射：**

| Java 类型                               | DSL ParamType |
| --------------------------------------- | ------------- |
| `String`                                | `"string"`    |
| `int` / `Integer` / `long` / `Long`     | `"integer"`   |
| `double` / `Double` / `float` / `Float` | `"number"`    |
| `boolean` / `Boolean`                   | `"boolean"`   |
| `List<?>`                               | `"array"`     |
| `Map<?,?>`                              | `"object"`    |

扫描后的方法通过 `ToolScanner.scan(bean)` 转换为 `ToolSpec`，再由 `LangChainToolBridge` 统一适配。

### 4.7 热加载语义（v1.1 新增）

系统支持对 `.agent.groovy` 脚本的热加载，规则如下：

```
文件系统变更 → HotReloader(WatchService) → DslCompiler → AgentRegistry
```

| 事件          | 行为                                     |
| ------------- | ---------------------------------------- |
| 文件创建/修改 | 重新编译 → 替换注册中心中同名 Agent 实例 |
| 文件删除      | 注销该文件中定义的所有 Agent             |

**保护机制：**
- **防抖**：同一文件 500ms 内的多次变更只触发一次重编译
- **原子替换**：新实例注册成功后才移除旧实例
- **异常隔离**：单文件编译失败不影响其他 Agent

### 4.8 安全沙箱规则（v1.1 新增）

沙箱模式（`enableSandbox = true`）下的安全限制：

| 安全措施       | 实现方式                                     | 描述                                  |
| -------------- | -------------------------------------------- | ------------------------------------- |
| **类白名单**   | `ImportCustomizer`                           | 仅允许导入 `com.agentdsl.core.spec.*` |
| **API 黑名单** | `SecureASTCustomizer.receiversBlackList`     | 见下表                                |
| **超时保护**   | `ExecutorService` + `Future.get(timeout)`    | 默认 30 秒，可配置                    |
| **包导入禁止** | `SecureASTCustomizer.packageAllowed = false` | 禁止 DSL 中直接 import                |

**黑名单 Receivers：**

| 类                           | 禁止原因              |
| ---------------------------- | --------------------- |
| `java.lang.System`           | 防止 `System.exit()`  |
| `java.lang.Runtime`          | 防止 `Runtime.exec()` |
| `java.lang.ProcessBuilder`   | 防止启动外部进程      |
| `java.lang.Thread`           | 防止创建线程          |
| `java.io.File`               | 防止文件系统直接访问  |
| `java.nio.file.Files`        | 防止文件系统直接访问  |
| `java.net.URL`               | 防止网络请求          |
| `java.net.HttpURLConnection` | 防止网络请求          |
| `java.net.Socket`            | 防止网络连接          |
| `java.net.ServerSocket`      | 防止监听端口          |

> [!CAUTION]
> 工具(`execute` 闭包)需要的外部访问（HTTP、数据库等）应通过工具的白名单机制显式声明，而非在 DSL 中直接调用。

### 4.9 v1.2 语义层更新说明

#### 4.9.1 工具生命周期与验证体系增强
在 v1.2 中，Compiler 阶段增加对 Tool 和 Parameter 的严谨校验。
Tools 运行时执行由 Executor 提供：
- **参数前置预校验**：验证 `pattern` 正则表达式匹配，数值的 `min/max` 界限，以及 `enumValues` 枚举成员存在性。
- **默认值回补**：若输入参数中未传递带有 `defaultValue` 的非 required 参数，自动填装回补。
- **安全拦截防抛出**：执行被 `CompletableFuture.orTimeout()` 包装，达到 `timeout` 则直接抛出失败；如果定义了 `onError` ，则提供优雅兜底输出。

#### 4.9.2 MCP 协议的接入范式
AgentDSL 作为 MCP Client 存在，在 Agent 注册 (`register`) 阶段将隐式地触发 MCP 服务连接的握手与 Tool 同步。
拉取到的 `toolSpecifications` 与 Executor 包装逻辑将汇聚到该 Agent 实例上下文中，并参与 `tools` 总览统计。运行结束随着实例销毁清理连接。

#### 4.9.3 提示词技能(Prompt Skill) 的注入原则
为了充分利用 Anthropic 等标准的 `*.skill.md` 生态库，DSL 会在 Agent 装配阶段将非 Logic 的 Prompt Skill （不论出自 `include` 还是 `includeFile`），**由内部展平追加注入到 Agent 的 `systemPrompt` 后部**，而非注册为假工具。避免触发语言模型的异常工具幻觉，确保核心品牌守则/系统预设立场的精准注入。

#### 4.9.4 安全沙箱白名单更新
对于内置 `file_read` 和 `file_write`，白名单通过当前工作目录 `System.getProperty("user.dir")` 衍生的 `/output`, `/tmp`, `/examples` 加以限流，脱离此限将报出 Access denied (对应 `ADSL-040` 沙箱越权异常)。

### 4.10 Autonomous 自主 Agent 语义（v1.4.0 新增）

#### 4.10.1 自主模式启用规则

当 `agent` 配置了 `autonomous { }` 块时，该 Agent 进入自主执行模式：
- `execution_mode "plan"`：执行前先由 `PlannerEngine` 生成多步计划，CLI 展示计划并等待用户确认后才开始执行
- `execution_mode "fast"`：跳过计划阶段，直接进入 ReAct 循环执行
- 自主模式下 CLI 使用 `--autonomous "目标描述"` 参数触发，而非 `--chat`

#### 4.10.2 ReAct 执行循环

```
用户意图
   ↓
PlannerEngine（plan 模式：生成计划 → 用户确认）
   ↓
AutonomousExecutor ReAct 循环:
  Thought（推理当前状态）→ Act（调用 Tool/Skill）→ Observe（获取结果）→ Reflect（判断完成/修正）
   ↓
达到 max_steps 或任务完成 → 返回 AutonomousResult
```

#### 4.10.3 max_steps 熔断机制

- 每次 Tool/Skill 调用计为 1 step
- 达到 `max_steps` 后，执行暂停，通过 `UserInteraction` 接口询问用户是否继续
- CLI 实现为 `ConsoleUserInteraction`，可通过 Java SPI 替换为 Web/API 实现

#### 4.10.4 Autonomous Agent 必填校验

| 字段             | 规则                                    | 错误码     |
| ---------------- | --------------------------------------- | ---------- |
| `execution_mode` | 必须为 `"plan"` 或 `"fast"`             | `ADSL-001` |
| `max_steps`      | 必须 > 0                                | `ADSL-001` |
| tools/skills     | 未配置任何工具时产生软性警告（不报错）  | 警告诊断   |

### 4.11 Workflow 直接执行语义（v1.4.0 新增）

#### 4.11.1 执行模式分发规则

`WorkflowExecutor` 在执行 SEQUENTIAL 步骤时，按以下优先顺序分发：

1. `executeClosure != null` → `executeCodeBlock()`：执行 Groovy 代码块
2. `toolRef != null` → `executeDirectToolCall()`：调用 `AgentRegistry.executeToolDirectly()`
3. `skillRef != null` → `executeDirectSkillCall()`：调用 `AgentRegistry.executeSkillDirectly()`
4. `mcpServerRef != null` → `executeDirectMcpCall()`：通过 MCP 工具名路由到注册的 ToolExecutor
5. `agentRef != null` → `executeAgentCall()`：原有 Agent 对话逻辑
6. 以上均为 null → 抛出 `DslRuntimeException`（错误码 `ADSL-031`）

#### 4.11.2 `execute` 闭包执行规则

- 闭包的 delegate 设为 `WorkflowExecutionContext`，resolveStrategy 为 `DELEGATE_FIRST`
- 闭包可接受 0 个参数（无参调用）或 1 个参数（接收 `WorkflowExecutionContext`）
- 闭包内可通过 `ctx.toolCall(name, params)` 调用已注册工具（包括内置工具）
- 闭包内可通过 `ctx.lastOutput`、`ctx.getStepResult(name)` 访问工作流上下文
- 闭包的返回值经 `output` 转换后写入 `WorkflowContext`

#### 4.11.3 `tool`/`skill` 直接调用的参数解析

`input` 闭包返回值的处理规则：
- 返回 `Map<String, Object>`：直接作为工具参数
- 返回其他类型：包装为 `{ "input": value }` Map
- 未定义 `input` 时：若 `lastOutput` 为 `Map` 则直接使用，否则包装为 `{ "input": lastOutput }`

#### 4.11.4 数据流不变性

所有五种执行模式共享相同的数据流规则：
- 步骤执行完成后均调用 `ctx.putStepResult(stepName, output)` 写入上下文
- `output` 闭包对所有执行模式均有效
- 并行、条件、循环等控制流结构内的子步骤均支持所有执行模式

---

## 5. 内置函数

| 函数       | 签名                                 | 描述                   |
| ---------- | ------------------------------------ | ---------------------- |
| `env`      | `env(String key): String`            | 读取环境变量           |
| `file`     | `file(String path): String`          | 读取文件内容作为字符串 |
| `resource` | `resource(String classpath): String` | 读取 classpath 资源    |
| `include`  | `include(String name): void`         | 引用已注册的 Tool      |

---

## 6. Provider 注册表

预置支持的 `provider` 标识及其对应的 LangChain4j 实现：

| Provider ID               | 目标 LangChain4j 类       | 必填配置  | 状态            | 默认取用环境变量          |
| ------------------------- | ------------------------- | --------- | --------------- | ------------------------- |
| `"openai"`                | `OpenAiChatModel`         | `apiKey`  | ✅ 已实现        | `OPENAI_API_KEY`          |
| `"ollama"`                | `OllamaChatModel`         | `baseUrl` | ✅ 已实现        | 无 (默认 localhost:11434) |
| `"deepseek"`              | 基于 OpenAI 兼容接口      | `apiKey`  | ✅ 已实现        | `DEEPSEEK_API_KEY`        |
| `"kimi"`, `"moonshot"`    | 基于 OpenAI 兼容接口      | `apiKey`  | ✅ 已实现        | `MOONSHOT_API_KEY`        |
| `"doubao"`                | 基于 OpenAI 兼容接口      | `apiKey`  | ✅ 已实现        | `DOUBAO_API_KEY`          |
| `"qwen"`                  | 基于 OpenAI 兼容接口      | `apiKey`  | ✅ 已实现        | `DASHSCOPE_API_KEY`       |
| `"zhipu"`, `"glm"`        | 基于 OpenAI 兼容接口      | `apiKey`  | ✅ 已实现        | `ZHIPU_API_KEY`           |
| `"minimax"`               | 基于 OpenAI 兼容接口      | `apiKey`  | ✅ 已实现        | `MINIMAX_API_KEY`         |
| `"claude"`, `"anthropic"` | `AnthropicChatModel`      | `apiKey`  | ✅ 已实现        | `ANTHROPIC_API_KEY`       |
| `"gemini"`, `"google"`    | `GoogleAiGeminiChatModel` | `apiKey`  | ✅ 已实现 (v1.2) | `GEMINI_API_KEY`          |

> 提示：对于采用 OpenAI 兼容格式的国内大模型提供商（如 Kimi, Doubao, Qwen, Zhipu, Minimax），`baselineUrl` 在未指定的情况下将分别自动路由到各厂商官网的 API 端点。

---

## 7. 完整示例

### 7.1 最小化 Agent

```groovy
agent("greeter") {
    model {
        provider "ollama"
        modelName "qwen2.5"
    }
    systemPrompt "你是一个友好的问候助手，用中文回复。"
}
```

### 7.2 带工具的 Agent

```groovy
// 独立定义可复用工具
tool("weatherQuery") {
    description "查询指定城市的天气信息"

    parameter {
        name "city"
        type "string"
        description "城市名称"
        required true
    }

    execute { params ->
        def city = params.city
        return "当前 ${city} 天气：晴，温度 25°C"
    }
}

agent("weather-assistant") {
    description "天气查询助手"

    model {
        provider "openai"
        modelName "gpt-4"
        apiKey env("OPENAI_API_KEY")
        temperature 0.3
    }

    systemPrompt "你是一个天气查询助手，帮助用户查询天气信息。"

    memory {
        type "message_window"
        maxMessages 10
    }

    tools {
        include "weatherQuery"
    }
}
```

### 7.3 结构化输出

```groovy
agent("sentiment-analyzer") {
    model {
        provider "openai"
        modelName "gpt-4"
    }

    systemPrompt "分析用户输入文本的情感倾向。"

    outputSchema {
        field "sentiment", "string", "情感类型：positive / negative / neutral"
        field "confidence", "double", "置信度 0.0 ~ 1.0"
        field "keywords", "list", "情感关键词列表"
    }
}
```

### 7.4 工作流 — 顺序执行

```groovy
workflow("translate-pipeline") {
    description "翻译质量保障流水线"

    steps {
        step("translate") {
            agent "translator"
            input { text -> text }
        }

        step("review") {
            agent "reviewer"
            input { translated -> "请审查以下翻译：\n${translated}" }
        }

        step("polish") {
            agent "polisher"
            input { review -> "根据审查意见润色：\n${review}" }
            output { result -> result }
        }
    }
}
```

### 7.5 工作流 — 并行执行

```groovy
workflow("multi-analysis") {
    description "多维度文档分析"

    steps {
        parallel {
            step("sentiment") {
                agent "sentiment-analyzer"
            }
            step("summary") {
                agent "summarizer"
            }
            step("keywords") {
                agent "keyword-extractor"
            }
        }

        step("combine") {
            agent "report-generator"
            input { results ->
                "情感分析: ${results.sentiment}\n" +
                "摘要: ${results.summary}\n" +
                "关键词: ${results.keywords}"
            }
        }
    }
}
```

### 7.6 工作流 — 条件路由

```groovy
workflow("support-pipeline") {
    description "客户工单处理流水线"

    steps {
        step("classify") {
            agent "classifier"
        }

        condition {
            check { result -> result.level }

            on "L1" {
                step("handle") {
                    agent "support-agent"
                }
            }

            on "L2" {
                step("escalate") {
                    agent "escalation-agent"
                }
                step("notify") {
                    agent "notification-agent"
                }
            }
        }
    }
}

### 7.7 工作流 — 循环迭代（v1.1 新增）

```groovy
workflow("refine-article") {
    description "文章打磨流水线"

    steps {
        step("draft") {
            agent "writer"
            input { topic -> "请撰写关于 ${topic} 的文章草稿" }
        }

        loop(maxIterations: 3) {
            step("review") {
                agent "reviewer"
            }

            // 当分数 >= 0.9 时跳出循环
            until { result -> result.score >= 0.9 }

            step("revise") {
                agent "writer"
                input { review -> "根据评审意见修改：${review.comments}" }
            }
        }
    }
}
```
```

### 7.7 Java 注解工具（v1.1 新增）

```java
public class CalculatorTools {
    @AgentTool(name = "add", description = "计算两数之和")
    public int add(@ToolParam(description = "第一个数") int a,
                   @ToolParam(description = "第二个数") int b) {
        return a + b;
    }
}
```

```groovy
// DSL 中引用注解工具
agent("math-assistant") {
    model {
        provider "ollama"
        modelName "qwen2.5"
    }
    tools {
        include "add"  // 由 ToolScanner 自动扫描注册
    }
}
```

### 7.8 增强工具定义示例

```groovy
tool("queryStock") {
    description "查询股票实时信息"
    returns "string", "包含当前价格与涨跌幅的 JSON 字符串"
    timeout 5

    permissions {
        network "https://api.finance.com/*"
    }

    parameter {
        name "symbol"
        type "string"
        description "股票代码，需大写"
        required true
        pattern "^[A-Z]{1,5}$"
    }

    parameter {
        name "detailed"
        type "boolean"
        description "是否包含详细数据"
        defaultValue false
    }

    onError { err ->
        return "查询异常，远端服务可能无响应：${err.message}"
    }

    execute { params ->
        //...
        return "{\"price\": 150.2}"
    }
}
```

### 7.9 接入 MCP 服务和外部技能文件（v1.2 新增）

```groovy
agent("mcp-powered-coder") {
    description "前端开发专家"

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        apiKey env("GEMINI_API_KEY")
    }

    // 载入 Anthropic 的标准规范 .skill.md
    skills {
        includeFile 'skills/brand-guidelines.skill.md'
    }

    mcp {
        // 连接一个基于 Node 的外部 GitHub MCP 服务
        server("github-service") {
            transport "stdio"
            command "npx", "-y", "@modelcontextprotocol/server-github"
            env "GITHUB_TOKEN", env("GITHUB_TOKEN")
            filterTools "get_issue", "create_issue"
        }
    }

    // 采用内置的文件读写工具
    tools {
        include "file_write"
        include "file_read"
    }
}
```

### 7.10 Autonomous 自主 Agent（v1.4.0 新增）

```groovy
// 定义若干工具
tool("web_search_mock") {
    description "搜索最新信息"
    parameter { name "query"; type "string"; description "搜索词"; required true }
    execute { params -> "关于 '${params.query}' 的搜索结果：[模拟返回]" }
}

tool("file_summary") {
    description "对文本生成摘要并保存"
    parameter { name "content"; type "string"; description "待摘要文本"; required true }
    parameter { name "output_path"; type "string"; description "保存路径"; required true }
    execute { params ->
        def summary = "【摘要】${params.content.take(100)}..."
        new File(params.output_path).text = summary
        return "摘要已保存到 ${params.output_path}"
    }
}

// 自主 Agent：plan 规划模式
agent("research-assistant") {
    description "自主研究助手，可查阅资料并生成摘要报告"

    model {
        provider "ollama"
        modelName "qwen3:14b"
    }

    autonomous {
        execution_mode "plan"   // 先生成计划，用户确认后执行
        max_steps 8             // 最多自主执行 8 步
    }

    tools {
        include "web_search_mock"
        include "file_summary"
    }

    systemPrompt "你是一个自主研究助手，帮助用户查阅资料并生成摘要。"
}

// 执行命令
// ./bin/agentdsl.sh run autonomous-demo.agent.groovy \
//   --autonomous "研究量子计算的最新进展，生成摘要报告保存到 /tmp/quantum.md"
```

### 7.11 Workflow 直接执行（v1.4.0 新增）

#### 7.11.1 纯代码步骤（execute）

```groovy
tool("format_timestamp") {
    description "格式化时间戳"
    execute { params ->
        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(new Date())
    }
}

workflow("code-execution-demo") {
    description "演示纯代码执行步骤"

    steps {
        // execute 步骤：直接执行 Groovy 代码，无需 LLM
        step("prepare-data") {
            execute { ctx ->
                def input = ctx.initialInput
                def items = input.split(",")
                return items.collect { it.trim().toUpperCase() }.join(" | ")
            }
        }

        // 后续步骤可使用 ctx.toolCall() 调用工具
        step("enrich") {
            execute { ctx ->
                def data = ctx.lastOutput
                def timestamp = ctx.toolCall("format_timestamp", [:])
                return "[${timestamp}] ${data}"
            }
        }
    }
}
```

#### 7.11.2 直接调用工具/技能/MCP

```groovy
tool("classify_priority") {
    description "按关键词分类优先级"
    parameter { name "text"; type "string"; required true }
    execute { params ->
        def text = params.text?.toLowerCase() ?: ""
        if (text.contains("紧急") || text.contains("故障")) return "P0"
        if (text.contains("重要") || text.contains("慢")) return "P1"
        return "P2"
    }
}

skill("report_formatter") {
    type "logic"
    description "格式化工单报告"
    parameter { name "content"; type "string"; required true }
    parameter { name "level"; type "string"; required true }
    execute { params ->
        return "【${params.level} 工单】${params.content}\n生成时间: ${new Date()}"
    }
}

agent("incident-handler") {
    model { provider "ollama"; modelName "qwen3:4b" }
    systemPrompt "你是 P0 故障处理专家，提供快速恢复方案。"
}

workflow("ticket-routing") {
    description "工单路由流水线：用工具分类，按级别路由"

    steps {
        // 直接调用工具（无 LLM）
        step("classify") {
            tool "classify_priority"
            input { text -> [text: text] }
        }

        // 条件路由
        condition {
            check { result -> result }

            on "P0" {
                // P0：LLM 认知节点分析处理
                step("p0-handle") {
                    agent "incident-handler"
                    input { level -> "P0 故障，请给出恢复方案：${ctx.getStepResult('prepare')}" }
                }
            }

            on "P1" {
                // P1/P2：直接调用 Skill 格式化，无 LLM
                step("p1-format") {
                    skill "report_formatter"
                    input { level -> [content: ctx.getStepResult('classify'), level: level] }
                }
            }

            on "P2" {
                step("p2-format") {
                    skill "report_formatter"
                    input { level -> [content: ctx.getStepResult('classify'), level: level] }
                }
            }
        }
    }
}
```

#### 7.11.3 混合编排（execute + agent）

```groovy
agent("data-analyst") {
    model { provider "ollama"; modelName "qwen3:4b" }
    systemPrompt "你是数据分析专家，根据提供的数据给出洞察报告。"
}

workflow("smart-report") {
    description "混合编排：代码预处理 + LLM 分析 + 代码后处理"

    steps {
        // ① 纯代码：数据预处理（无 LLM）
        step("preprocess") {
            execute { ctx ->
                def raw = ctx.initialInput
                // 模拟数据清洗
                return raw.replaceAll(/\s+/, " ").trim()
            }
        }

        // ② LLM 认知：AI 分析（有 LLM）
        step("analyze") {
            agent "data-analyst"
            input { cleaned -> "请分析以下数据并给出洞察：\n${cleaned}" }
        }

        // ③ 纯代码：后处理格式化（无 LLM）
        step("format-report") {
            execute { ctx ->
                def analysis = ctx.lastOutput
                def ts = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date())
                return """
# 数据分析报告 (${ts})

## 分析结果
${analysis}

---
*本报告由 AgentDSL 混合编排流水线自动生成*
""".trim()
            }
        }
    }
}
```

---

## 8. 错误代码

编译器和运行时应产生标准化的错误代码：

| 错误码     | 类别   | 描述                                                         |
| ---------- | ------ | ------------------------------------------------------------ |
| `ADSL-001` | 编译期 | 缺少必填配置项                                               |
| `ADSL-002` | 编译期 | DSL 脚本编译失败                                             |
| `ADSL-003` | 编译期 | 脚本执行超时（沙箱模式）                                     |
| `ADSL-004` | 编译期 | Step 执行模式冲突：指定了多个互斥模式，或未指定任何执行模式  |
| `ADSL-005` | 编译期 | 类型不匹配                                                   |
| `ADSL-006` | 编译期 | 安全违规（使用了禁止的 API）                                 |
| `ADSL-010` | 运行时 | 工具引用未找到                                               |
| `ADSL-011` | 运行时 | Agent 引用未找到                                             |
| `ADSL-012` | 运行时 | 环境变量 / 文件 / 资源缺失                                   |
| `ADSL-013` | 运行时 | Skill 引用未找到                                             |
| `ADSL-014` | 运行时 | MCP 服务器或工具引用未找到                                   |
| `ADSL-020` | 运行时 | 模型调用超时                                                 |
| `ADSL-021` | 运行时 | 模型调用异常                                                 |
| `ADSL-022` | 运行时 | 自主 Agent max_steps 触发熔断（非错误，等待用户确认）        |
| `ADSL-030` | 运行时 | 工具执行异常                                                 |
| `ADSL-031` | 运行时 | 步骤执行模式未定义（step 无法分发）                          |
| `ADSL-032` | 运行时 | execute 闭包执行异常                                         |
| `ADSL-040` | 安全   | 沙箱资源超限                                                 |
| `ADSL-041` | 安全   | 输入/输出校验失败                                            |

---

## 9. 版本演进策略

| 版本             | 内容                                                                                           | 兼容性     |
| ---------------- | ---------------------------------------------------------------------------------------------- | ---------- |
| **v1.0**         | `agent`, `tool`, 基础语法，LangChain4j 集成                                                    | 基线       |
| **v1.1**         | 工作流语法重设计、`@AgentTool` 注解发现、热加载、安全沙箱增强                                  | 向后兼容   |
| **v1.2**         | MCP 组网、Skill/提示词注入、内建 Tool 强化、沙箱白名单                                         | 向后兼容   |
| **v1.3**         | 数据基础设施（datasource）、扩充内置工具、browser_use、搜索引擎配置、`--debug` 全链路追踪      | 向后兼容   |
| **v1.4.0**（当前）| Autonomous 自主 Agent（ReAct 循环 + max_steps 熔断）、Workflow 直接执行（execute/tool/skill/mcp）| 向后兼容   |
| **v2.0**         | 事件驱动多 Agent 协作、`event` / `subscribe` 关键字                                            | 可能不兼容 |

> [!NOTE]
> v2.0 的多 Agent 事件驱动协作机制将作为独立设计文档另行规划。

---

## 10. 附录：DSL 到 LangChain4j API 的映射表

| DSL 声明                               | LangChain4j API (1.x)                                 |
| -------------------------------------- | ----------------------------------------------------- |
| `agent("name") { ... }`                | `AiServices.builder(interface).build()`               |
| `model { provider "openai" ... }`      | `OpenAiChatModel.builder()...build()`                 |
| `systemPrompt "..."`                   | `SystemMessage.from("...")`                           |
| `memory { type "message_window" ... }` | `MessageWindowChatMemory.withMaxMessages(n)`          |
| `memory { type "token_window" ... }`   | `TokenWindowChatMemory.builder()...build()`           |
| `tools { include "name" }`             | `ToolSpecification` + `ToolExecutor`                  |
| `tool("name") { execute { ... } }`     | `ToolSpecification.builder()` + `ToolExecutor` lambda |
| `@AgentTool` 注解方法                  | `ToolSpecification` + 反射方法调用                    |
| `rag { contentRetriever { ... } }`     | `EmbeddingStoreContentRetriever.builder()...build()`  |
| `outputSchema { field ... }`           | `AiServices.builder(StructuredOutputInterface.class)` |
| `workflow { steps { ... } }`           | 自定义 `WorkflowExecutor`（v1.1 规划）                |
