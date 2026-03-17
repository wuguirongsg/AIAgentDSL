# AgentDSL 开发者入门指南

> **文档版本**: v1.4 · 适用 AgentDSL v1.4.0  
> **目标读者**: 初次使用 AgentDSL 的开发者

---

## 目录

1. [快速开始](#1-快速开始)
2. [核心概念](#2-核心概念)
3. [第一个 Agent：Hello World](#3-第一个-agenthello-world)
4. [给 Agent 添加工具 (Tools)](#4-给-agent-添加工具-tools)
5. [使用 Workflow 编排多 Agent](#5-使用-workflow-编排多-agent)
6. [工作流进阶：并行、条件、循环](#6-工作流进阶并行条件循环)
7. [Workflow 直接执行模式（v1.4.0）](#7-workflow-直接执行模式v140)
8. [技能系统 (Skills)](#8-技能系统-skills)
9. [MCP 协议集成](#9-mcp-协议集成)
10. [自主 Agent（Autonomous，v1.4.0）](#10-自主-agentautonomousv140)
11. [记忆与安全护栏](#11-记忆与安全护栏)
    11.1 [记忆 (Memory)](#111-记忆-memory)
    11.2 [安全护栏 (Guardrails)](#112-安全护栏-guardrails)
12. [CLI 命令行参考](#12-cli-命令行参考)
13. [常见问题 (FAQ)](#13-常见问题-faq)

---

## 1. 快速开始

### 1.1 环境要求

| 依赖       | 版本要求     | 用途            |
| ---------- | ------------ | --------------- |
| JDK        | 17+          | 运行 AgentDSL   |
| Gradle     | 8.x (已内置) | 构建项目        |
| Node.js    | 18+ (可选)   | 运行 MCP Server |
| 大模型 API | 至少一个     | Agent 底层推理  |

### 1.2 构建项目

```bash
# 克隆项目
git clone <repo-url> && cd AgentDSL

# 构建（首次会自动下载依赖）
./shell/build.sh
```

构建完成后会在 `agentdsl-cli/build/libs/` 下产生 `agentdsl.jar`。

构建完成后，你可以使用 `./shell/agentdsl.sh` 运行脚本。为了方便使用，建议将 `shell` 目录加入 PATH 或创建一个 `bin` 软链接。

### 1.3 配置 API Key

AgentDSL 通过环境变量读取 API Key，根据你使用的模型提供商设置对应变量：

```bash
# Gemini (Google)
export GEMINI_API_KEY=your-key

# OpenAI
export OPENAI_API_KEY=your-key

# Ollama (本地模型，无需 Key，默认端口 11434)
# 无需额外配置
```

> 完整的 Provider 列表见 [语言规范 §6](lang-spec/AgentDSL-Language-Spec-v1.4.md)，支持 OpenAI、Ollama、Gemini、Claude、DeepSeek、Kimi、通义千问、豆包、智谱、MiniMax 等。

### 1.4 运行第一个脚本

```bash
./shell/agentdsl.sh run examples/simple-chat.agent.groovy --chat "你好"
```

---

## 2. 核心概念

在编写任何脚本之前，先理解 AgentDSL 的 **四个核心构件**：

```
┌─────────────────────────────────────────────────────┐
│                   .agent.groovy 脚本                 │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │  Agent    │  │  Agent    │  │  Agent    │          │
│  │(带 model  │  │(带 tools  │  │(带 MCP   │          │
│  │ + prompt) │  │ + skills) │  │ + memory)│          │
│  └──────────┘  └──────────┘  └──────────┘           │
│        │              │              │               │
│        ▼              ▼              ▼               │
│  ┌──────────────────────────────────────────────┐   │
│  │          Workflow (编排多个 Agent)             │   │
│  │  step → step → parallel → condition → loop   │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

| 构件         | 作用                        | 最简形式                               |
| ------------ | --------------------------- | -------------------------------------- |
| **Agent**    | 一个具备 LLM 能力的智能体   | `agent('name') { model { ... } }`      |
| **Tool**     | Agent 可调用的函数/工具     | `tool('name') { execute { ... } }`     |
| **Workflow** | 编排多个 Agent 协作完成任务 | `workflow('name') { steps { ... } }`   |
| **Skill**    | 可复用的提示词或逻辑模块    | `skill('name') { type 'prompt'; ... }` |

### 2.1 一个脚本中可以定义什么？

一个 `.agent.groovy` 脚本可以包含 **任意数量** 的顶层声明，它们之间的关系是：

```groovy
// ① 定义工具 (全局可复用)
tool('myTool') { ... }

// ② 定义技能 (全局可复用)
skill('mySkill') { ... }

// ③ 定义 Agent (引用工具和技能)
agent('agentA') { tools { include 'myTool' } }
agent('agentB') { skills { include 'mySkill' } }

// ④ 定义 Workflow (引用已定义的 Agent)
workflow('myFlow') { steps { step('s1') { agent 'agentA' } } }
```

> **关键理解**：多个 Agent 定义在同一文件中时，它们是 **独立的**，彼此之间没有隐式关系。只有通过 Workflow 才能把它们串联起来协作。

### 2.2 执行模式

AgentDSL 支持三种 CLI 执行模式：

| 模式                 | CLI 参数                               | 说明                                            |
| -------------------- | -------------------------------------- | ----------------------------------------------- |
| **Agent 对话**       | `--chat "消息"`                        | 向单个 Agent 发送消息并获取回复                 |
| **Workflow 执行**    | `--workflow 名称 --input "输入"`       | 执行一个多步骤工作流（支持直接执行节点）         |
| **Autonomous 自主**  | `--autonomous "目标描述"`              | 启动自主 Agent 的 ReAct 循环（v1.4.0）           |

```bash
# 模式 1: 与单个 Agent 对话
./shell/agentdsl.sh run script.agent.groovy --chat "你好"

# 模式 2: 执行 Workflow
./shell/agentdsl.sh run script.agent.groovy --workflow my-flow --input "待处理的文本"

# 模式 3: 自主 Agent
./shell/agentdsl.sh run script.agent.groovy --autonomous "分析最新 AI 论文并生成摘要"
```

> **注意**：`--chat`、`--workflow`、`--autonomous` 三者互斥，每次执行只能选其一。

---

## 3. 第一个 Agent：Hello World

### 3.1 最简 Agent

创建文件 `examples/my-first.agent.groovy`：

```groovy
agent('greeter') {
    model {
        provider 'gemini'
        modelName 'gemini-2.0-flash'
    }
    systemPrompt '你是一个友好的问候助手，用中文回复。'
}
```

运行：

```bash
./shell/agentdsl.sh run examples/my-first.agent.groovy --chat "你好，介绍一下你自己"
```

### 3.2 Agent 的完整结构

```groovy
agent('name') {
    description '可选的描述文字'          // Agent 用途说明

    model {                               // 【必填】LLM 模型配置
        provider 'gemini'                 //   模型提供商
        modelName 'gemini-2.5-flash'      //   模型名称
        temperature 0.7                   //   温度 (0.0~2.0)
        maxTokens 2048                    //   最大输出 token
    }

    systemPrompt '你的角色设定...'         // 系统提示词

    memory { ... }                        // 记忆配置 (可选)
    tools { ... }                         // 工具集 (可选)
    skills { ... }                        // 技能集 (可选)
    mcp { ... }                           // MCP 协议服务 (可选)
    guardrails { ... }                    // 安全护栏 (可选)
    outputSchema { ... }                  // 结构化输出 (可选)
}
```

> 只有 `model` 块是必填的（需要 `provider` + `modelName`），其他全部可选。

### 3.3 模型参数配置 (customSetting)

`customSetting` 用于设置模型特定的参数，不同 Provider 支持的参数有所不同：

**通用支持 Provider**：OpenAI、Claude、DeepSeek、Kimi、Doubao、Qwen、Zhipu、Minimax 等通过 `customParameters` 全部支持。

**特殊支持 Provider**：

| Provider | 支持参数 | 说明 |
|----------|---------|------|
| **Ollama** | `think`, `returnThinking`, `stop`, `minP`, `responseFormat` | 本地模型特定参数 |
| **Gemini** | `seed`, `frequencyPenalty`, `presencePenalty`, `returnThinking`, `sendThinking`, `stopSequences` | Google AI 特定参数 |

**Ollama 示例：**

```groovy
agent('think-agent') {
    model {
        provider 'ollama'
        modelName 'qwen3:14b'
        customSetting 'think', true            // 启用思考模式
        customSetting 'returnThinking', true   // 返回思考过程
        customSetting 'stop', ['###', 'END']   // 停止词列表
    }
    systemPrompt '你是一个思考型助手。'
}
```

**Gemini 示例：**

```groovy
agent('gemini-agent') {
    model {
        provider 'gemini'
        modelName 'gemini-2.0-flash'
        customSetting 'seed', 42                // 固定随机种子
        customSetting 'returnThinking', true    // 返回思考过程
        customSetting 'stopSequences', ['END']  // 停止序列
    }
    systemPrompt '你是一个智能助手。'
}
```

### 3.4 指定目标 Agent

如果脚本中定义了多个 Agent，默认用第一个。可用 `--agent` 指定：

```bash
# 脚本中有 agentA 和 agentB，指定 agentB
./shell/agentdsl.sh run script.agent.groovy --agent agentB --chat "你好"
```

---

## 4. 给 Agent 添加工具 (Tools)

工具让 Agent 具备调用外部功能的能力（如查天气、读文件、发 HTTP 请求）。

### 4.1 使用内置工具

AgentDSL 预置了涵盖多种不同数据形态维度的 14 个基础工具，直接 `include` 即可：

| 内置工具          | 功能                            |
| ----------------- | ------------------------------- |
| `http_get`        | 发送 GET 请求                   |
| `http_post`       | 发送 POST 请求                  |
| `json_parse`      | 解析 JSON 字符串                |
| `json_query`      | JSONPath 查询                   |
| `file_read`       | 读取本地文件                    |
| `file_write`      | 写入本地文件                    |
| `excel_read`      | 读取 Excel 文件 (v1.3)          |
| `excel_write`     | 写入 Excel 文件 (v1.3)          |
| `pdf_read`        | 读取 PDF 离线提取并识别 (v1.3)  |
| `image_recognize` | 分析与识别图片 (v1.3)           |
| `cmd_execute`     | 执行有黑名单和超时的本地 Shell  |
| `db_query`        | 读取 JDBC 数据库 (v1.3)         |
| `db_execute`      | JDBC 连接的 DDL/DML 操作 (v1.3) |
| `web_search`      | 实时互联网搜索 (v1.3)           |
| `groovy_execute`  | 执行 Groovy 代码 (v1.4)         |
| `shell_script_run`| 执行 Shell/Bat/PowerShell (v1.4)|
| `python_run`      | 执行 Python 代码 (v1.4)         |

```groovy
agent('my-agent') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你可以读写文件和发送 HTTP 请求。'
    tools {
        include 'http_get'
        include 'file_read'
        include 'file_write'
    }
}
```

### 4.2 自定义工具（独立定义）

在脚本顶层定义工具，然后在 Agent 中引用：

```groovy
// 顶层定义：全局可复用
tool('weatherQuery') {
    description '查询指定城市的天气信息'

    parameter {
        name 'city'
        type 'string'
        description '城市名称'
        required true
    }

    execute { params ->
        def city = params.city
        return "当前 ${city} 天气：晴，温度 25°C"
    }
}

// Agent 中通过 include 引用
agent('weather-assistant') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是天气查询助手。'
    tools {
        include 'weatherQuery'
    }
}
```

### 4.3 自定义工具（内联定义）

也可以直接在 Agent 的 `tools` 块内定义：

```groovy
agent('calculator') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是一个计算助手。'
    tools {
        include 'http_get'   // 引用内置工具

        tool('calculate') {   // 内联定义
            description '计算数学表达式'
            returns 'string', '计算结果'
            timeout 10

            parameter {
                name 'expression'
                type 'string'
                description "数学表达式"
                required true
            }

            execute { params ->
                def result = new GroovyShell().evaluate(params.expression)
                return "结果: ${result}"
            }

            onError { err -> "计算失败: ${err}" }
        }
    }
}
```

### 4.4 工具参数增强 (v1.2)

参数支持丰富的校验规则：

```groovy
parameter {
    name 'score'
    type 'integer'
    description '评分'
    required true
    min 0              // 最小值
    max 100            // 最大值
    defaultValue 60    // 默认值
}

parameter {
    name 'level'
    type 'string'
    description '等级'
    enumValues 'A,B,C,D'   // 枚举限制
}

parameter {
    name 'email'
    type 'string'
    description '邮箱'
    pattern '^[\\w+@\\w+\\.\\w+]+$'  // 正则校验
}
```

### 4.5 集成数据库数据源 (v1.3)

AgentDSL 支持在全局配置并安全挂载外部的 JDBC 数据库源：

```groovy
// 全局配置（顶层级别）
datasource('my_h2') {
    type 'h2'
    url 'jdbc:h2:file:./data/mydb;DB_CLOSE_DELAY=-1'
    username 'sa'
    password ''
}

agent('data_assistant') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    
    // 明确告诉大模型它拥有的数据源名称
    systemPrompt "你可以使用内置数据源 'my_h2' 进行数据库查询和更新。"
    
    tools {
        include 'db_query'   // 引入查询工具
        include 'db_execute' // 引入变更工具
    }
    
    datasources {
        attach 'my_h2'       // 为当前 Agent 挂载关联连接
    }
}
```

### 4.6 开启原生浏览器集成 (v1.3)

不需要外部独立运行复杂的 MCP 服务器，只需在一行配置就能获得基于 Playwright 的强大浏览器控制套件：

```groovy
agent('web-crawler') {
    model { provider 'gemini'; modelName 'gemini-2.5-pro' }
    systemPrompt '你是一个网络信息搜集专家，请通过浏览器工具执行自动操作。'

    browser_use {
        sandbox false            // false: 真实显示浏览器界面（用于直观调试）
        hitl_on "click", "fill"  // 触发点击或填充信息时开启人工批准环路
    }
}

### 4.7 Web Search 实时搜索 (v1.3)

AgentDSL 提供了强大的内置搜索工具 `web_search`，支持 Tavily、Serper、智谱等多种后端。你可以在 Agent 中全局配置搜索参数：

```groovy
agent('search-assistant') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    
    // 全局搜索配置
    search {
        provider 'tavily'            // 搜索引擎：tavily, serper, zhipu
        apiKey env("TAVILY_API_KEY") // 对应的 API Key
        maxResults 5                 // 每次搜索返回的结果数
    }

    tools {
        include 'web_search'
    }

    systemPrompt '你是一个联网助手，可以查询最新实时信息。'
}
```

运行对话：
```bash
./shell/agentdsl.sh run script.agent.groovy --chat "今天有哪些最新的 AI 新闻？"
```

> **安全提示**：使用 `web_search` 需要在 `search` 块或环境变量中正确配置对应的 API Key。建议使用 `env()` 函数保护密钥。

```

---

## 5. 使用 Workflow 编排多 Agent

Workflow 是 AgentDSL 最强大的特性——将多个 Agent 按顺序、并行、条件或循环组合起来完成复杂任务。

### 5.1 基本概念：步骤 (Step) 与数据传递

**Workflow 的核心机制是数据流**——每个 step 的输出自动成为下一个 step 的输入：

```
  --input 用户输入
        │
        ▼
  ┌─── step 1 ───┐    step 1 的输出
  │ agent: agentA │──────────────────▶ ┌─── step 2 ───┐    step 2 的输出
  │ input: { }    │                    │ agent: agentB │──────────────────▶ 最终输出
  └───────────────┘                    │ input: { }    │
                                       └───────────────┘
```

**数据传递规则**：

| 场景                   | 传入值                                          |
| ---------------------- | ----------------------------------------------- |
| 第一个 step            | `--input` 参数值（或空字符串）                  |
| 后续 step (无 `input`) | 上一个 step 的 **原始输出** 直接传入            |
| 后续 step (有 `input`) | 上一个 step 输出经 `input` 闭包 **转换** 后传入 |
| workflow 最终结果      | 最后一个 step 的输出（或经 `output` 闭包转换）  |

### 5.2 第一个 Workflow

```groovy
// 定义两个 Agent
agent('translator') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是翻译员。用户给你中文，你翻译成英文。只输出翻译结果。'
}

agent('reviewer') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是翻译审核员。审核翻译的准确性和流畅性，给出评价。'
}

// 定义 Workflow，将两个 Agent 串联
workflow('translate-and-review') {
    description '翻译并审核'

    steps {
        step('translate') {
            agent 'translator'
            // input 闭包：将用户输入包装为翻译指令
            input { text -> "请翻译以下文本：\n${text}" }
        }

        step('review') {
            agent 'reviewer'
            // 上一个 step 的输出（翻译结果）作为参数传入
            input { translated -> "请审核以下翻译：\n${translated}" }
        }
    }
}
```

运行：

```bash
./shell/agentdsl.sh run my-workflow.agent.groovy \
  --workflow translate-and-review \
  --input "人工智能正在改变世界"
```

### 5.3 `input` 和 `output` 闭包详解

```groovy
step('my-step') {
    agent 'myAgent'

    // input 闭包：接收上一个 step 的输出，返回值作为 Agent 的输入消息
    input { previousResult ->
        "请基于以下材料工作：\n\n${previousResult}"
    }

    // output 闭包（可选）：对 Agent 的回复做后处理
    output { agentReply ->
        agentReply.trim()     // 例如去掉首尾空白
    }
}
```

- **不写 `input`**：上一步的输出原样传给 Agent
- **不写 `output`**：Agent 的回复原样传给下一步
- 闭包参数名可以任意取（`ctx`、`data`、`text` 等），只是一个形参

---

## 6. 工作流进阶：并行、条件、循环

### 6.1 并行执行 (parallel)

多个 step 同时执行，所有结果合并后传给后续步骤：

```groovy
workflow('multi-analysis') {
    description '并行多维度分析'

    steps {
        // 三个分析同时进行
        parallel {
            step('sentiment') { agent 'sentiment-analyzer' }
            step('keywords')  { agent 'keyword-extractor' }
        }

        // 汇总报告（接收 parallel 的合并结果）
        step('report') {
            agent 'report-generator'
            input { results -> "分析结果：${results}" }
        }
    }
}
```

### 6.2 条件路由 (condition)

根据上一步的结果动态选择下游分支：

```groovy
workflow('quality-aware-format') {
    description '根据质量选择格式化方案'

    steps {
        step('assess') {
            agent 'reviewer'
            input { text -> "评估质量，返回 premium 或 standard：\n${text}" }
        }

        condition {
            // check 闭包：接收 assess 输出, 返回分支名
            check { result ->
                result.toString().trim().toLowerCase().contains('premium')
                    ? 'premium' : 'standard'
            }

            // 匹配 'premium' 走这个分支
            on('premium') {
                step('premium-format') { agent 'premium-formatter' }
            }

            // 匹配 'standard' 走这个分支
            on('standard') {
                step('standard-format') { agent 'standard-formatter' }
            }
        }
    }
}
```

### 6.3 循环迭代 (loop)

反复执行直到满足退出条件或达到最大次数：

```groovy
workflow('translate-pipeline') {
    steps {
        step('translate') {
            agent 'translator'
            input { text -> "请翻译：\n${text}" }
        }

        // 最多循环 3 次
        loop(maxIterations: 3) {
            // 第一步：审核
            step('review') {
                agent 'reviewer'
                input { translated -> "请审核：\n${translated}" }
            }

            // 退出条件：审核通过则跳出循环
            until { result -> result.toString().trim().equalsIgnoreCase('pass') }

            // 未通过时执行：根据反馈修改
            step('revise') {
                agent 'translator'
                input { feedback -> "请根据反馈修改：\n${feedback}" }
            }
        }
    }
}
```

**loop 执行流程**：

```
      ┌────────────────────────┐
      │    step('review')      │◀─────────────────┐
      └───────────┬────────────┘                   │
                  ▼                                 │
          ┌──────────────┐    是                    │
          │ until 条件?   │───────▶ 退出循环         │
          └──────┬───────┘                          │
                 │ 否                               │
                 ▼                                  │
      ┌────────────────────────┐                   │
      │    step('revise')      │───────────────────┘
      └────────────────────────┘
            (迭代 ≤ maxIterations)
```

---

## 7. Workflow 直接执行模式（v1.4.0）

在传统 Workflow 中，每个 step 都要调用一个 Agent（LLM）来执行。v1.4.0 引入了**直接执行模式**，让 step 可以直接运行代码、调用工具、调用技能或调用 MCP 工具，完全绕过 LLM，消除不必要的"LLM Tax"（延迟、费用、幻觉风险）。

### 7.1 五种执行模式概览

| 模式      | 关键字    | LLM | 描述                                   |
| --------- | --------- | --- | -------------------------------------- |
| 认知节点  | `agent`   | ✅   | 引用 Agent，LLM 推理（原有行为）       |
| 代码节点  | `execute` | ❌   | 直接执行 Groovy 闭包                   |
| 工具节点  | `tool`    | ❌   | 直接调用已注册工具（全局或内置）       |
| 技能节点  | `skill`   | ❌   | 直接调用已注册 Logic Skill             |
| MCP 节点  | `mcp`     | ❌   | 直接调用指定 MCP 服务器上的工具        |

> 每个 step 必须且只能选择其中一种模式，不可混用。

### 7.2 execute：纯代码执行

`execute` 闭包接收一个 `WorkflowExecutionContext` 参数，提供访问工作流上下文和调用工具的能力：

```groovy
workflow("code-demo") {
    steps {
        step("format-data") {
            execute { ctx ->
                // ctx.initialInput  - 工作流原始输入
                // ctx.lastOutput    - 上一步输出
                // ctx.getStepResult("step-name") - 按名称获取步骤结果
                def items = ctx.initialInput.split(",")
                return items.collect { it.trim().toUpperCase() }.join(" | ")
            }
        }

        step("add-timestamp") {
            execute { ctx ->
                def data = ctx.lastOutput
                // 通过 ctx.toolCall() 调用任意已注册工具
                def ts = ctx.toolCall("format_timestamp", [:])
                return "[${ts}] ${data}"
            }
        }
    }
}
```

**`WorkflowExecutionContext` 常用 API：**

| API                                     | 说明                        |
| --------------------------------------- | --------------------------- |
| `ctx.initialInput`                      | 工作流初始输入              |
| `ctx.lastOutput`                        | 上一步骤输出                |
| `ctx.getStepResult("step-name")`        | 按名称读取任意已完成步骤结果 |
| `ctx.getAllStepResults()`               | 获取全部步骤结果 Map        |
| `ctx.toolCall("tool-name", [k: v, ...])` | 直接调用已注册工具          |

### 7.3 tool：直接工具调用

`tool` 模式绕过 LLM，直接执行已注册工具（全局 `tool()` 定义或内置工具）：

```groovy
tool("classify_priority") {
    description "根据文本内容分类处理优先级"
    parameter { name "text"; type "string"; required true }
    execute { params ->
        def text = params.text?.toLowerCase() ?: ""
        if (text.contains("故障") || text.contains("宕机")) return "P0"
        if (text.contains("慢") || text.contains("延迟")) return "P1"
        return "P2"
    }
}

workflow("classify-demo") {
    steps {
        step("classify") {
            tool "classify_priority"
            // input 闭包返回 Map 作为工具参数
            input { text -> [text: text] }
        }
    }
}
```

### 7.4 skill：直接技能调用

`skill` 模式直接调用已注册的 Logic Skill（Prompt Skill 不支持直接调用）：

```groovy
skill("report_formatter") {
    type "logic"
    description "格式化报告"
    parameter { name "content"; type "string"; required true }
    parameter { name "level"; type "string"; required false }
    execute { params ->
        return "【${params.level ?: '普通'}】${params.content}"
    }
}

workflow("format-demo") {
    steps {
        step("format") {
            skill "report_formatter"
            input { data -> [content: data, level: "重要"] }
        }
    }
}
```

### 7.5 mcp：直接 MCP 工具调用

`mcp` 模式直接调用连接的 MCP Server 上的特定工具：

```groovy
workflow("mcp-direct-demo") {
    steps {
        step("list-files") {
            mcp "filesystem-server", "list_directory"
            input { path -> [path: path] }
        }
    }
}
```

> MCP Server 需要在脚本中的某个 Agent 上配置并注册，`mcp` 步骤引用该 Agent 所连接的 server name。

### 7.6 混合编排：最佳实践

将确定性任务（数据清洗、格式化、分类）分配给直接执行模式，将需要推理的任务交给 Agent，实现最优的成本/质量平衡：

```groovy
workflow("smart-pipeline") {
    steps {
        // ① 确定性：代码清洗数据（免 LLM）
        step("clean") {
            execute { ctx -> ctx.initialInput.trim().replaceAll(/\s+/, " ") }
        }

        // ② 推理：LLM 生成洞察（必须 LLM）
        step("analyze") {
            agent "data-analyst"
            input { cleaned -> "请分析以下数据：\n${cleaned}" }
        }

        // ③ 确定性：代码格式化报告（免 LLM）
        step("format") {
            execute { ctx ->
                def analysis = ctx.lastOutput
                def date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date())
                return "# 报告（${date}）\n\n${analysis}"
            }
        }
    }
}
```

---

## 8. 技能系统 (Skills)

Skill 是比 Tool 更高层的抽象，分为两种类型：

| 类型             | 用途                                      | 执行方式     |
| ---------------- | ----------------------------------------- | ------------ |
| **Prompt Skill** | 注入角色设定 / 风格指南到 systemPrompt 中 | LLM 推理     |
| **Logic Skill**  | 封装确定性的 Groovy 代码逻辑              | 本地代码执行 |

### 7.1 Prompt Skill（提示词类型）

Prompt Skill 的 `instruction` 会在 Agent 装配阶段 **自动追加** 到 Agent 的 `systemPrompt` 末尾：

```groovy
skill('summarizeText') {
    type 'prompt'
    description '对给定文本生成简洁的摘要'
    instruction '''
        你是一位专业的文字摘要助手。
        请用 3 句以内的中文归纳核心要点。
        不要遗漏关键信息，不要添加个人观点。
    '''
}

agent('assistant') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是一个内容助手。'
    skills {
        include 'summarizeText'
    }
}
// 效果：Agent 实际的 systemPrompt = "你是一个内容助手。" + summarizeText 的 instruction
```

### 7.2 Logic Skill（逻辑类型）

Logic Skill 以工具的形式注册，Agent 通过 Function Calling 触发执行：

```groovy
skill('formatAsMarkdownList') {
    type 'logic'
    description '将逗号分隔的字符串格式化为 Markdown 有序列表'

    parameter {
        name 'items'
        type 'string'
        description "逗号分隔的列表项，例如 '苹果,香蕉,橙子'"
        required true
    }

    execute { params ->
        def items = params.items?.split(',') ?: []
        def sb = new StringBuilder()
        items.eachWithIndex { item, idx ->
            sb.append("${idx + 1}. ${item.trim()}\n")
        }
        return sb.toString().trim()
    }
}
```

### 7.3 加载外部 `.skill.md` 文件

AgentDSL 支持直接加载 Anthropic 标准格式的 `.skill.md` 文件：

```groovy
agent('brandWebAgent') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是一位品牌设计师。'
    skills {
        includeFile 'skills/brand-guidelines.skill.md'
    }
}
```

> `.skill.md` 文件的内容会被解析后按 Prompt Skill 方式注入到 Agent 的系统提示中。

---

## 9. MCP 协议集成

MCP (Model Context Protocol) 让 Agent 可以 **零代码** 接入外部工具服务（如 GitHub API、文件系统、Playwright 等）。

### 8.1 基本用法

```groovy
agent('file-analyst') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是一个文件分析师，可以读取本地文件。'

    mcp {
        server('filesystem-server') {
            transport 'stdio'             // 传输方式：stdio 或 http
            timeout 60                    // 连接超时(秒)
            command 'npx', '-y', '@modelcontextprotocol/server-filesystem', '/tmp'
        }
    }
}
```

### 8.2 挂载多个 MCP Server

一个 Agent 可以同时接入多个 MCP Server：

```groovy
agent('multi-tool') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你可以访问 GitHub 和本地文件。'

    mcp {
        server('github') {
            transport 'stdio'
            command 'npx', '-y', '@modelcontextprotocol/server-github'
            env 'GITHUB_TOKEN', System.getenv('GITHUB_TOKEN')
        }

        server('filesystem') {
            transport 'stdio'
            command 'npx', '-y', '@modelcontextprotocol/server-filesystem', '/tmp'
        }
    }
}
```

### 8.3 MCP 工作原理

```
AgentDSL (MCP Client)                    MCP Server (外部进程)
    │                                         │
    │── 启动进程 (npx ...) ─────────────────▶ │
    │◀─ 握手 + 返回 toolSpecifications ──────│
    │                                         │
    │   Agent 对话中…                         │
    │── 调用工具 (list_directory, etc.) ─────▶│
    │◀─ 返回工具结果 ────────────────────────│
    │                                         │
    │── Agent 对话结束, 关闭连接 ────────────▶│
```

> MCP Server 在 Agent 注册阶段自动启动和握手，拉取到的工具会自动注册到 Agent 上。Agent 实例销毁时自动关闭连接。

---

## 10. 自主 Agent（Autonomous，v1.4.0）

自主 Agent 实现了"意图 → 规划 → 执行 → 观察 → 修正"的 ReAct 循环，用户只需描述目标，Agent 自主决策并多步完成任务。

### 10.1 启用自主模式

在 Agent 上添加 `autonomous { }` 块即可开启：

```groovy
agent('research-assistant') {
    model {
        provider 'ollama'
        modelName 'qwen3:14b'
    }

    autonomous {
        execution_mode 'plan'       // 'plan'（规划后确认执行）或 'fast'（直接执行）
        max_steps 10                // 超过 10 步后暂停询问用户
        auto_discover_mcp true      // Agent 未配置工具时，自动从 MCP 仓库发现匹配工具（可选）
    }

    tools {
        include 'web_search'
        include 'file_write'
    }

    systemPrompt '你是一个自主研究助手，帮助用户查阅资料并生成摘要报告。'
}
```

### 10.2 两种执行模式

| 模式     | 说明                                                         | 适用场景               |
| -------- | ------------------------------------------------------------ | ---------------------- |
| `"plan"` | 先由 PlannerEngine 生成多步计划，CLI 展示计划并等待用户确认后再执行 | 重要操作、高风险任务  |
| `"fast"` | 跳过计划阶段，直接进入 ReAct 循环执行                        | 简单任务、快速响应     |

### 10.3 运行自主 Agent

使用 `--autonomous` 参数（而非 `--chat`）触发自主执行：

```bash
# plan 模式：先展示计划，确认后执行
./shell/agentdsl.sh run autonomous-demo.agent.groovy \
  --autonomous "研究量子计算最新进展，并将摘要保存到 /tmp/quantum-summary.md"

# 输出示例（plan 模式）：
# === 执行计划 ===
# Step 1: 搜索 "量子计算 2026 最新研究"
# Step 2: 搜索 "量子纠错突破进展"
# Step 3: 整理要点并生成摘要
# Step 4: 将摘要保存到 /tmp/quantum-summary.md
# 
# 是否确认执行？[Y/n]:
```

### 10.4 MCP 自动发现（auto_discover_mcp）

当 Agent 的 `autonomous` 块中配置了 `auto_discover_mcp true` 时，如果 Agent 没有预配置任何工具，系统会自动从 MCP 仓库中搜索并挂载匹配任务目标的工具。

**使用场景**：
- 不确定需要哪些工具，希望 Agent 自主判断
- 任务目标包含明确的工具需求（如"使用 playwright 访问网页"）
- 快速原型验证，不想手动配置 tools

**示例**：

```groovy
agent('dynamic-tool-agent') {
    model {
        provider 'openai'
        modelName 'gpt-4'
    }

    autonomous {
        execution_mode 'fast'
        max_steps 15
        auto_discover_mcp true   // 开启 MCP 自动发现
    }

    // 注意：tools 块未配置任何工具

    systemPrompt '你是一个通用任务助手，根据用户需求自动选择合适的工具完成任务。'
}
```

**执行流程**：

```
1. 用户: --autonomous "搜索 GitHub 上 agentdsl 项目的最新提交"
2. Agent 发现自身无工具 → 触发 MCP 自动发现
3. 从 MCP 仓库搜索匹配 "GitHub" 和 "搜索" 的工具
4. 自动挂载 github-mcp-server
5. 使用 github_search_commits 工具完成任务
```

> **注意**：自动发现每轮最多执行 1 次（避免无限循环），且仅在没有配置任何工具时触发。如果 Agent 已配置 tools，则不会触发自动发现。

### 10.5 max_steps 熔断机制

当自主循环执行步数超过 `max_steps` 时，系统自动暂停并询问：

```
[自主 Agent] 已执行 10 步，任务尚未完成。
当前状态：已搜集 3 篇论文，正在汇总内容...

是否继续执行？[Y=继续 / N=中止 / S=显示当前结果]:
```

### 10.6 ReAct 执行流程

```
用户输入目标
      │
      ▼
  [plan 模式]     [fast 模式]
PlannerEngine  ──────────────────┐
生成计划           直接开始循环   │
用户确认                          │
      │                          │
      ▼                          ▼
  ┌─────────────────────────────────────┐
  │             ReAct 循环              │
  │  Thought → Act(Tool/Skill) → Observe│
  │              │                      │
  │         Reflect (规划中)              │
  └──────────────┬──────────────────────┘
                 │ 完成 or max_steps 熔断
                 ▼
           AutonomousResult
```

---

## 11. 记忆与安全护栏

### 11.1 记忆 (Memory)

记忆让 Agent 在多轮对话中保留上下文（在 Workflow 的单次执行中通常不需要）：

```groovy
agent('chat-bot') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是一个聊天机器人。'

    memory {
        type 'message_window'   // 按消息条数保留
        maxMessages 10          // 最多保留 10 条
    }
}
```

| 记忆类型         | 说明              | 适用场景           |
| ---------------- | ----------------- | ------------------ |
| `message_window` | 保留最近 N 条消息 | 一般对话           |
| `token_window`   | 按 token 数量保留 | 需要精确控制上下文 |

### 11.2 安全护栏 (Guardrails)

限制 Agent 的行为边界：

```groovy
agent('safe-agent') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是一个安全的助手。'

    guardrails {
        maxTokensPerRequest 4000           // 单次最大 token
        blockedTopics '政治', '暴力'       // 封禁话题

        inputValidator { input ->          // 自定义输入校验
            !input.contains('恶意内容')
        }

        outputValidator { output ->        // 自定义输出校验
            output.length() < 10000
        }
    }
}
```

---

## 12. CLI 命令行参考

### 12.1 命令总览

```bash
# 运行脚本（对话模式）
./shell/agentdsl.sh run <script> --chat "消息"

# 运行脚本（交互式会话模式，支持多轮对话）
./shell/agentdsl.sh run <script> --interactive

# 运行脚本（workspace 模式，指定 Agent）
./shell/agentdsl.sh run <script> --agent <agent名> --chat "消息"

# 运行脚本（工作流模式）
./shell/agentdsl.sh run <script> --workflow <workflow名> --input "输入"

# 运行脚本（带调试功能，输出包含耗时、工具调用全链路追踪）
./shell/agentdsl.sh run <script> --workflow <workflow名> --input "输入" --debug

# 运行脚本（自主 Agent 模式，v1.4.0）
./shell/agentdsl.sh run <script> --autonomous "任务目标描述"

# 验证脚本语法
./shell/agentdsl.sh validate <script>

# 列出脚本中的定义
./shell/agentdsl.sh list <script>
```

### 12.2 参数详解

| 参数           | 缩写 | 说明                                            |
| -------------- | ---- | ----------------------------------------------- |
| `--chat`       | `-c` | 向 Agent 发送的消息文本                         |
| `--agent`      | `-a` | 目标 Agent 名称（默认第一个）                   |
| `--interactive`| `-i` | 启动交互式会话模式，支持多轮对话                 |
| `--workflow`   | `-w` | 要执行的 Workflow 名称                          |
| `--input`      |      | Workflow 的初始输入文本                         |
| `--autonomous` |      | 目标描述，触发自主 Agent 执行（v1.4.0）         |
| `--sandbox`    |      | 启用安全沙箱（默认 false）                      |
| `--debug`      |      | 开启调试追踪并显示详细执行流（替代旧版 trace）  |

### 12.3 执行模式选择指南

| 场景                        | 使用方式                            |
| --------------------------- | ----------------------------------- |
| 与单个 Agent 简单对话       | `--chat "你的消息"`                 |
| 与 Agent 多轮对话           | `--interactive`                     |
| 多 Agent 协作完成任务       | `--workflow xxx --input "..."`      |
| 测试某个特定 Agent          | `--agent name --chat "..."`         |
| 需要流水线和数据传递        | `--workflow xxx --input "..."`      |
| 目标导向、自主多步执行任务  | `--autonomous "完成XXX任务"` (v1.4.0) |

### 12.4 交互式会话模式

使用 `--interactive` 或 `-i` 参数启动交互式会话模式，支持与 Agent 进行多轮对话：

```bash
./shell/agentdsl.sh run script.agent.groovy --interactive
```

**交互式会话功能：**

| 命令 | 功能 |
|------|------|
| `/help` | 显示帮助信息 |
| `/exit`, `/quit`, `q` | 退出会话 |
| `/clear` | 清屏 |
| `/restart` | 重新加载 Agent |

**交互式会话特点：**
- 自动保存对话历史
- 支持上下文记忆
- 提供友好的命令行界面

---

## 13. 常见问题 (FAQ)

### Q: 用 `--chat` 时，脚本里的 Workflow 会执行吗？

**不会。** `--chat` 只做单 Agent 对话。要运行 Workflow 必须用 `--workflow` 参数。

### Q: 一个脚本里定义了多个 Agent，`--chat` 用哪个？

默认使用 **脚本中第一个定义的 Agent**。可用 `--agent <名称>` 来指定。

### Q: Workflow 中 step 的 input 闭包参数是什么？

- 第一个 step：参数是 `--input` 传入的字符串
- 后续 step：参数是上一个 step 的 Agent 输出结果

### Q: Prompt Skill 和 Logic Skill 有什么区别？

| 区别     | Prompt Skill                 | Logic Skill                |
| -------- | ---------------------------- | -------------------------- |
| 执行者   | LLM                          | Groovy 代码                |
| 注入方式 | 追加到 systemPrompt          | 注册为可调用的工具         |
| 适用场景 | 角色设定、风格指南、知识注入 | 数据格式化、API 调用、计算 |

### Q: MCP Server 需要预先启动吗？

**不需要。** AgentDSL 引擎会在 Agent 注册时自动启动并管理 MCP Server 进程的生命周期。

### Q: `tool` 和 `skill(type: 'logic')` 有什么区别？

都是可调用的函数，主要区别在于设计意图：
- `tool`：细粒度的函数工具（查天气、发请求）
- `logic skill`：封装较完整的业务逻辑片段（数据转换、多步处理）

功能上两者等价，编译后都注册为 LangChain4j 的 ToolSpecification。

### Q: Workflow step 里的 `execute`/`tool`/`skill` 和 Agent 里的工具有什么区别？（v1.4.0）

| 维度         | Agent 内 tool（via LLM）                 | Step 直接执行（execute/tool/skill）          |
| ------------ | ---------------------------------------- | -------------------------------------------- |
| 触发方式     | LLM 自主决策调用                         | 工作流编排层强制执行                         |
| LLM 参与     | ✅ 是                                     | ❌ 否                                         |
| 延迟/费用    | 高（每次经过 LLM 推理）                  | 低（直接代码执行）                           |
| 确定性       | 低（LLM 可能选错工具或跳过）             | 高（编排层保证必定执行）                     |
| 适用场景     | 需要 LLM 判断何时调用、如何调用          | 确定性的数据处理、格式化、分类等             |

### Q: `--autonomous` 和 `--workflow` 什么时候各用什么？（v1.4.0）

| 场景                           | 推荐模式       |
| ------------------------------ | -------------- |
| 任务步骤已知、数据流程固定     | `--workflow`   |
| 目标模糊、步骤需要动态规划     | `--autonomous` |
| 需要重复执行、可复现的流水线   | `--workflow`   |
| 探索性任务、Agent 自主决策路径 | `--autonomous` |

### Q: `execute` 闭包里可以访问什么数据？（v1.4.0）

```groovy
step("my-step") {
    execute { ctx ->
        ctx.initialInput           // 工作流最初的 --input 值
        ctx.lastOutput             // 上一步的输出
        ctx.getStepResult("name")  // 任意已完成步骤的输出
        ctx.toolCall("tool-name", [param: value])  // 调用工具
    }
}
```

### Q: step 里 `tool`/`skill` 模式的 `input` 闭包应该返回什么？（v1.4.0）

返回一个 `Map`，key 对应工具/技能的参数名称：

```groovy
step("classify") {
    tool "my_classifier"
    input { lastResult -> [text: lastResult, threshold: 0.8] }
}
```

如果不写 `input`，系统会尝试将上一步输出直接作为参数（Map 类型）传入，或包装为 `{input: lastOutput}`。

---

## 附录 A: 示例索引

| 示例文件                                   | 演示特性                                              | 版本   |
| ------------------------------------------ | ----------------------------------------------------- | ------ |
| `simple-chat.agent.groovy`                 | 最简 Agent，入门                                      | v1.0   |
| `tool-agent.agent.groovy`                  | 自定义工具 + 工具引用                                 | v1.0   |
| `enhanced-tools.agent.groovy`              | 内置工具 + 增强工具定义（timeout, onError）           | v1.2   |
| `workflow-pipeline.agent.groovy`           | 顺序/并行/条件/循环 全部工作流类型                    | v1.1   |
| `skill-demo.agent.groovy`                  | Prompt Skill + Logic Skill 定义                       | v1.2   |
| `skill-comparison.agent.groovy`            | 两种 Skill 类型对比                                   | v1.2   |
| `brand-homepage.agent.groovy`              | includeFile 加载外部 .skill.md                        | v1.2   |
| `mcp-github.agent.groovy`                  | MCP GitHub Server 集成                                | v1.2   |
| `mcp-multi-agent.agent.groovy`             | MCP + 多 Agent Workflow                               | v1.2   |
| `database-report.agent.groovy`             | 内置 HTTP 工具 + Workflow                             | v1.3   |
| `workflow-direct-execution.agent.groovy`   | execute/tool/skill/mcp 直接执行 + 混合编排            | v1.4.0 |
| `autonomous-agent.agent.groovy`            | Autonomous 自主 Agent（plan 模式 + fast 模式对比）    | v1.4.0 |
| `code-executor-demo.agent.groovy`         | 代码执行工具（Groovy/Shell/Python）                   | v1.4   |

---

> 📖 **深入了解**：[AgentDSL 语言定义规范 v1.4.0](lang-spec/AgentDSL-Language-Spec-v1.4.md) · [架构与扩展指南](Architecture_Guide_zh-CN.md)
