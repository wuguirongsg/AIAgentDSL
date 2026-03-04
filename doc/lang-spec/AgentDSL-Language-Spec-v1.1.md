# AgentDSL 语言定义规范 v1.1

> 本文档是 AgentDSL 的正式语言规范，定义了 DSL 的语法结构、关键字、类型系统和语义规则。
> 所有 DSL 引擎的实现必须遵循本规范。

> [!NOTE]
> v1.1 相对 v1.0 的主要变更：
> - 重新设计工作流语法（`steps`/`parallel`/`condition` 替代 `flow`/`start`/`node`）
> - 新增 `@AgentTool`/`@ToolParam` 注解工具发现机制
> - 新增热加载语义
> - 新增安全沙箱规则（超时保护、AST 黑名单）
> - 更新 Provider 表和 LangChain4j API 映射

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

| 关键字     | 描述                    | 必填参数       |
| ---------- | ----------------------- | -------------- |
| `agent`    | 定义一个 Agent          | `name: String` |
| `tool`     | 定义一个可复用工具      | `name: String` |
| `workflow` | 定义一个多 Agent 工作流 | `name: String` |

#### Agent 块关键字（Agent Block Keywords）

仅可出现在 `agent { }` 闭包内部。

| 关键字         | 描述               | 类型     | 必填   |
| -------------- | ------------------ | -------- | ------ |
| `description`  | Agent 的用途描述   | `String` | 否     |
| `model`        | LLM 模型配置块     | `Block`  | **是** |
| `systemPrompt` | 系统提示词         | `String` | 否     |
| `memory`       | 记忆配置块         | `Block`  | 否     |
| `tools`        | 工具集配置块       | `Block`  | 否     |
| `rag`          | RAG 检索增强配置块 | `Block`  | 否     |
| `guardrails`   | 安全护栏配置块     | `Block`  | 否     |
| `outputSchema` | 结构化输出定义块   | `Block`  | 否     |

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

#### Tools 块关键字

| 关键字    | 描述             | 用法                   |
| --------- | ---------------- | ---------------------- |
| `include` | 引用已注册的工具 | `include "toolName"`   |
| `tool`    | 内联定义一个工具 | `tool("name") { ... }` |

#### Tool 定义关键字（`tool { }` 内部）

| 关键字        | 描述                        | 类型      | 必填   |
| ------------- | --------------------------- | --------- | ------ |
| `description` | 工具描述（LLM 可见）        | `String`  | **是** |
| `parameter`   | 定义一个输入参数            | `Block`   | 否     |
| `execute`     | 工具执行逻辑（Groovy 闭包） | `Closure` | **是** |

#### Parameter 定义关键字

| 关键字        | 描述     | 类型        |
| ------------- | -------- | ----------- |
| `name`        | 参数名   | `String`    |
| `type`        | 参数类型 | `ParamType` |
| `description` | 参数描述 | `String`    |
| `required`    | 是否必填 | `Boolean`   |

`ParamType` 枚举值：`"string"`, `"integer"`, `"double"`, `"boolean"`, `"list"`, `"map"`

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

---

## 3. 语法规则（Grammar）

以下使用 EBNF 风格描述 AgentDSL 的语法结构：

```ebnf
(* === 顶层 === *)
script          ::= { topLevelDecl } ;
topLevelDecl    ::= agentDecl | toolDecl | workflowDecl ;

(* === Agent 声明 === *)
agentDecl       ::= "agent" "(" STRING ")" "{" agentBody "}" ;
agentBody       ::= { agentProperty } ;
agentProperty   ::= descriptionProp
                   | modelBlock
                   | systemPromptProp
                   | memoryBlock
                   | toolsBlock
                   | ragBlock
                   | guardrailsBlock
                   | outputSchemaBlock ;

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
                   | parameterDecl
                   | "execute" CLOSURE ;
parameterDecl   ::= "parameter" "{" { paramProp } "}" ;
paramProp       ::= "name" STRING
                   | "type" STRING
                   | "description" STRING
                   | "required" BOOLEAN ;

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
stepBody        ::= "agent" STRING
                   | "input" CLOSURE
                   | "output" CLOSURE ;

parallelStep    ::= "parallel" "{" { sequentialStep } "}" ;

conditionStep   ::= "condition" "{" conditionBody "}" ;
conditionBody   ::= "check" CLOSURE { conditionBranch } ;
conditionBranch ::= "on" STRING "{" { stepEntry } "}" ;

loopStep        ::= "loop" "(" "maxIterations" ":" INTEGER ")" "{" { stepEntry } untilClause { stepEntry } "}" ;
untilClause     ::= "until" CLOSURE ;

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

| 构件       | 必填项                                      |
| ---------- | ------------------------------------------- |
| `agent`    | `name`, `model.provider`, `model.modelName` |
| `tool`     | `name`, `description`, `execute`            |
| `workflow` | `name`, `steps`（至少 1 个 step）           |

违反必填规则时，编译器应抛出 `DslCompilationException`，包含清晰的错误位置和缺失字段名。

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

| Provider ID  | 目标 LangChain4j 类  | 必填配置            | 状态     |
| ------------ | -------------------- | ------------------- | -------- |
| `"openai"`   | `OpenAiChatModel`    | `apiKey`            | ✅ 已实现 |
| `"ollama"`   | `OllamaChatModel`    | `baseUrl`           | ✅ 已实现 |
| `"deepseek"` | 通过 OpenAI 兼容接口 | `apiKey`, `baseUrl` | ✅ 已实现 |

> 系统应支持通过 SPI 机制注册自定义 Provider。后续版本将按需添加更多 Provider（DashScope、智谱等）。

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

---

## 8. 错误代码

编译器和运行时应产生标准化的错误代码：

| 错误码     | 类别   | 描述                         |
| ---------- | ------ | ---------------------------- |
| `ADSL-001` | 编译期 | 缺少必填配置项               |
| `ADSL-002` | 编译期 | DSL 脚本编译失败             |
| `ADSL-003` | 编译期 | 脚本执行超时（沙箱模式）     |
| `ADSL-004` | 编译期 | 安全违规（使用了禁止的 API） |
| `ADSL-005` | 编译期 | 类型不匹配                   |
| `ADSL-010` | 运行时 | 工具引用未找到               |
| `ADSL-011` | 运行时 | Agent 引用未找到             |
| `ADSL-012` | 运行时 | 环境变量 / 文件 / 资源缺失   |
| `ADSL-020` | 运行时 | 模型调用超时                 |
| `ADSL-021` | 运行时 | 模型调用异常                 |
| `ADSL-030` | 运行时 | 工具执行异常                 |
| `ADSL-040` | 安全   | 沙箱资源超限                 |
| `ADSL-041` | 安全   | 输入/输出校验失败            |

---

## 9. 版本演进策略

| 版本             | 内容                                                          | 兼容性     |
| ---------------- | ------------------------------------------------------------- | ---------- |
| **v1.0**         | `agent`, `tool`, 基础语法，LangChain4j 集成                   | 基线       |
| **v1.1**（当前） | 工作流语法重设计、`@AgentTool` 注解发现、热加载、安全沙箱增强 | 向后兼容   |
| **v2.0**         | 事件驱动多 Agent 协作、`event` / `subscribe` 关键字           | 可能不兼容 |

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
