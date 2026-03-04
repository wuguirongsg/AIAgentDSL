# AgentDSL 语言定义规范 v1.2

> 本文档是 AgentDSL 的正式语言规范，定义了 DSL 的语法结构、关键字、类型系统和语义规则。
> 所有 DSL 引擎的实现必须遵循本规范。

> [!NOTE]
> v1.2 相对 v1.1 的主要变更：
> - **核心重构**：ToolSpec 工具定义深度增强（新增 `returns`、`timeout`、`onError`、`permissions` 及 Parameter 的高级校验如 `pattern`、`min`、`max`、`enumValues`）。
> - **内置能力**：引入 `BuiltinToolRegistry`，内置 `http_get`, `http_post`, `json_parse`, `json_query`, `file_read`, `file_write` 工具。
> - **生态接入**：新增 `mcp` 协议支持，允许 Agent 无缝连接基于 Model Context Protocol 的工具服务器（支持 stdio/http）。
> - **知识管理**：新增 `skill` 技能体系支持，支持定义和加载 `PROMPT` (提示词) 和 `LOGIC` (代码逻辑) 技能，支持 `.skill.md` 外部文件 `includeFile` 集成。
> - **新提供商**：支持 Gemini API。

---

## 1. 概述

### 1.1 设计原则

| 原则             | 说明                                                       |
| ---------------- | ---------------------------------------------------------- |
| **声明式优先**   | 用户描述 Agent "是什么"，而非"怎么做"                      |
| **约定优于配置** | 合理的默认值，最小化必填项                                 |
| **类型安全**     | 编译期校验关键配置，减少运行时错误                         |
| **可组合**       | 所有构件（Agent、Tool、Memory、Skill）可独立定义并自由组合 |
| **渐进式复杂度** | 简单场景 10 行可用，复杂场景按需扩展                       |

### 1.2 文件约定

| 项目       | 约定                                                                |
| ---------- | ------------------------------------------------------------------- |
| 文件扩展名 | `.agent.groovy`                                                     |
| 文件编码   | UTF-8                                                               |
| 顶层声明   | 每个文件允许多个顶层声明（`agent`、`tool`、`workflow`、`skill` 等） |

---

## 2. 词法结构

### 2.1 关键字（Keywords）

AgentDSL 的关键字分为以下层级：

#### 顶层关键字（Top-Level Keywords）

定义独立的 DSL 构件，只能出现在脚本最外层。

| 关键字     | 描述                      | 必填参数       |
| ---------- | ------------------------- | -------------- |
| `agent`    | 定义一个 Agent            | `name: String` |
| `tool`     | 定义一个可复用工具        | `name: String` |
| `workflow` | 定义一个多 Agent 工作流   | `name: String` |
| `skill`    | 定义一个可复用技能 (v1.2) | `name: String` |

#### Agent 块关键字（Agent Block Keywords）

仅可出现在 `agent { }` 闭包内部。

| 关键字         | 描述                 | 类型     | 必填   |
| -------------- | -------------------- | -------- | ------ |
| `description`  | Agent 的用途描述     | `String` | 否     |
| `model`        | LLM 模型配置块       | `Block`  | **是** |
| `systemPrompt` | 系统提示词           | `String` | 否     |
| `memory`       | 记忆配置块           | `Block`  | 否     |
| `tools`        | 工具集配置块         | `Block`  | 否     |
| `skills`       | 技能集配置块 (v1.2)  | `Block`  | 否     |
| `mcp`          | MCP 服务配置块(v1.2) | `Block`  | 否     |
| `rag`          | RAG 检索增强配置块   | `Block`  | 否     |
| `guardrails`   | 安全护栏配置块       | `Block`  | 否     |
| `outputSchema` | 结构化输出定义块     | `Block`  | 否     |

#### Model 块关键字

| 关键字        | 描述                         | 类型      | 默认值                  |
| ------------- | ---------------------------- | --------- | ----------------------- |
| `provider`    | 模型提供商标识               | `String`  | 必填                    |
| `modelName`   | 模型名称                     | `String`  | 必填                    |
| `apiKey`      | API 密钥（支持环境变量引用） | `String`  | `env("OPENAPI_KEY")` 等 |
| `baseUrl`     | 自定义 API 端点              | `String`  | 按 provider 默认        |
| `temperature` | 温度参数                     | `Double`  | `0.7`                   |
| `topP`        | Top-P 采样                   | `Double`  | `1.0`                   |
| `maxTokens`   | 最大输出 token 数            | `Integer` | `2048`                  |
| `timeout`     | 请求超时（秒）               | `Integer` | `60`                    |

#### Tools 块关键字 (v1.2增强)

| 关键字    | 描述                         | 用法                   |
| --------- | ---------------------------- | ---------------------- |
| `include` | 引用已注册的工具 或 内置工具 | `include "toolName"`   |
| `tool`    | 内联定义一个工具             | `tool("name") { ... }` |

> 系统内置工具预注册名：`http_get`, `http_post`, `json_parse`, `json_query`, `file_read`, `file_write`。可直接使用 `include` 引入。

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

---

## 3. 语法规则（Grammar 节选）

基于 v1.1 更新：

```ebnf
(* === 顶层 === *)
topLevelDecl    ::= agentDecl | toolDecl | workflowDecl | skillDecl ;

(* === Tool 增强 === *)
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
paramProp       ::= "name" STRING | "type" STRING | "description" STRING 
                   | "required" BOOLEAN | "pattern" STRING 
                   | "defaultValue" ANY | "enumValues" STRING 
                   | "min" DOUBLE | "max" DOUBLE ;

(* === MCP 增强 === *)
mcpBlock        ::= "mcp" "{" { mcpServerDecl } "}" ;
mcpServerDecl   ::= "server" "(" STRING ")" "{" serverProp "}" ;
serverProp      ::= "transport" STRING
                   | "command" STRING { "," STRING }
                   | "env" STRING "," STRING
                   | "url" STRING
                   | "filterTools" STRING { "," STRING } ;

(* === Skill 增强 === *)
skillsBlock     ::= "skills" "{" { skillEntry } "}" ;
skillEntry      ::= "include" STRING | "includeFile" STRING ;
skillDecl       ::= "skill" "(" STRING ")" "{" skillBody "}" ;
skillBody       ::= "type" STRING | "description" STRING 
                   | "instruction" MULTILINE_STRING
                   | parameterDecl | "executeBody" CLOSURE ;
```

---

## 4. 语义层更新说明

### 4.1 工具生命周期与验证体系增强
在 v1.2 中，Compiler 阶段增加对 Tool 和 Parameter 的严谨校验。
Tools 运行时执行由 Executor 提供：
- **参数前置预校验**：验证 `pattern` 正则表达式匹配，数值的 `min/max` 界限，以及 `enumValues` 枚举成员存在性。
- **默认值回补**：若输入参数中未传递带有 `defaultValue` 的非 required 参数，自动填装回补。
- **安全拦截防抛出**：执行被 `CompletableFuture.orTimeout()` 包装，达到 `timeout` 则直接抛出失败；如果定义了 `onError` ，则提供优雅兜底输出。

### 4.2 MCP 协议的接入范式
AgentDSL 作为 MCP Client 存在，在 Agent 注册 (`register`) 阶段将隐式地触发 MCP 服务连接的握手与 Tool 同步。
拉取到的 `toolSpecifications` 与 Executor 包装逻辑将汇聚到该 Agent 实例上下文中，并参与 `tools` 总览统计。运行结束随着实例销毁清理连接。

### 4.3 提示词技能(Prompt Skill) 的注入原则
为了充分利用 Anthropic 等标准的 `*.skill.md` 生态库，DSL 会在 Agent 装配阶段将非 Logic 的 Prompt Skill （不论出自 `include` 还是 `includeFile`），**由内部展平追加注入到 Agent 的 `systemPrompt` 后部**，而非注册为假工具。避免触发语言模型的异常工具幻觉，确保核心品牌守则/系统预设立场的精准注入。

### 4.4 安全沙箱白名单更新
对于内置 `file_read` 和 `file_write`，白名单通过当前工作目录 `System.getProperty("user.dir")` 衍生的 `/output`, `/tmp`, `/examples` 加以限流，脱离此限将报出 Access denied (对应 `ADSL-040` 沙箱越权异常)。

---

## 5. 完整示例 (v1.2)

### 5.1 增强工具定义示例

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
        pattern "^[A-Z]{1,5}\$"
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

### 5.2 接入 MCP 服务和外部技能文件

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

---

## 6. Provider 注册表更新 (v1.2 全面支持)

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
