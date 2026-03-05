# AgentDSL 架构与扩展指南

> **文档版本**: v1.0
> **目标读者**: 核心开发者，贡献者，系统架构师

## 1. 系统架构概览

AgentDSL 被设计为一个模块化、可扩展且类型安全的 Java 应用 AI Agent 构建框架。其核心理念是上层提供声明式的 DSL，而在底层保持极其严格的工程标准。

### 1.1 核心模块

项目被划分为多个高内聚的模块：

*   **`agentdsl-core`**: 基础核心模块。包含 DSL 引擎（基于 Groovy）、抽象语法树（AST）定义（如 `AgentSpec`, `ToolSpec` 等 `spec` 模型），以及核心的编译器/验证器接口。
    *   *关键类*: `AgentDslEngine`, `DslBaseScript`, 位于 `com.agentdsl.core.spec` 下的 POJO 类。
*   **`agentdsl-compiler`**: 负责将 `.agent.groovy` 代码解析为 `core` 中定义的 AST 模型。它在执行前负责进行结构和语义上的合法性校验。
    *   *关键类*: `DslCompiler`, `DslValidator`。
*   **`agentdsl-langchain4j`**: 声明式模型与底层 LangChain4j 实现之间的桥梁。它负责将配置（如 `ModelSpec`）转换为可执行的 LangChain4j 对象（如 `ChatLanguageModel`, `ToolSpecification`）。
    *   *关键类*: `LangChainModelFactory`, `LangChainToolBridge`。
*   **`agentdsl-mcp`**: 实现了模型上下文协议 (Model Context Protocol, MCP) 客户端。这是一个关键模块，允许 AgentDSL 将标准的 MCP Server 挂载为原生的本地工具。
    *   *关键类*: `McpClientFactory`, `McpToolProviderBridge`。
*   **`agentdsl-tools`**: 一个内置的工具库（如 HTTP, 数据库, Excel, File IO 等）。它还提供了基于 `@AgentTool` 注解的机制，用于从用户代码中扫描本地方法作为工具。
    *   *关键类*: `BuiltinToolRegistry`, `ToolScanner`。
*   **`agentdsl-runtime`**: 执行引擎模块。主要管理 Agent 的生命周期，执行 ReAct 或自定义的循环调度，并管理记忆/状态。
    *   *关键类*: `AgentExecutor`, `AgentRegistry`, `WorkflowExecutor`。
*   **`agentdsl-cli`**: 命令行交互界面，用于无缝启动和测试脚本。
    *   *关键类*: `RunCommand`, `ListCommand`, `ValidateCommand`。

### 1.2 执行管道 (Execution Pipeline)

1.  **解析阶段 (Parsing)**: `agentdsl-core` 和 `agentdsl-compiler` 解析脚本，并构建出 `AgentSpec`, `ToolSpec`, `SkillSpec` 等模型。
2.  **验证阶段 (Validation)**: 对 AST 的必填字段、逻辑正确性以及安全沙箱约束进行校验。
3.  **注册阶段 (Registration)**: 校验通过的规格说明（Specs）将被注册到 `AgentRegistry` 中。此时技能（Skills）将被解析并展平成工具格式（`ToolSpec`）。
4.  **创建阶段 (Creation)**: 当一个 Agent 被调用时，`agentdsl-langchain4j` 会将这些规格定义转换为具体的 LangChain4j 的 `AiServices` 和内部模型。
5.  **执行阶段 (Execution)**: `agentdsl-runtime` 引导具体的执行器（如标准、工作流流转或自动化驱动）来启动执行。

---

## 2. SPI 扩展指南

AgentDSL 深度依赖服务提供者接口 (Service Provider Interface, SPI) 模式，允许企业级用户在不修改核心框架代码的情况下注入自定义逻辑。

### 2.1 扩展大模型接口 (Model Provider)

如果想要添加一个新的大语言模型供应商（例如本地化的开源私有模型），你需要实现一个能解析 `model {}` 块的工厂。

1.  **实现工厂接口**（通常是继承 LangChain bridge 中的某个基础实现）。
2.  你的工厂需要检查 `ModelSpec.provider` 字段（例如 `"my-custom-llm"`）。
3.  如果匹配，解析该节点中的 `baseUrl`、`apiKey` 及其它自定义参数，并返回一个 LangChain4j 内部的 `ChatLanguageModel` 实例。
4.  通过 Java SPI 机制注册你的工厂（在 `META-INF/services/your.factory.Interface` 中声明）。

### 2.2 扩展工具系统 (Tool System)

目前可以通过三种方式来提供自定义工具：

#### A. DSL 内联 `tool` 闭包
直接在 `.agent.groovy` 脚本文件中通过闭包定义工具。这非常适合轻量级、针对特定脚本的即时逻辑。

#### B. 注解扫描 (`@AgentTool`)
这是编写健壮、类型安全的 Java 后端代码最推荐的方式。

```java
public class MyCompanyTools {
    @AgentTool(name = "query_user", description = "从内部 CRM 查询用户详情.")
    public String queryUser(@ToolParam(description = "User ID") String userId) {
        // 在这里编写你现存的 Java 业务逻辑
        return crmClient.getUser(userId).toJson();
    }
}
```
你只需将这个对象注册给引擎，`ToolScanner` 扫描器会自动处理并转换为大模型所需的 LangChain4j 工具规格。

#### C. MCP 协议 (生态官方推荐方式)
如果你的工具是通过 Python、Node.js 编写的，或者你想直接复用现有的社区工具（例如 Playwright 浏览器, GitHub 仓库管理, Postgres 数据库读写），你甚至不需要编写 一行 Java 代码。

直接将外部工具作为 MCP Server 提供，并在 DSL 中挂载：
```groovy
mcp {
    server("github") {
        transport "stdio"
        command "npx", "-y", "@modelcontextprotocol/server-github"
        env "GITHUB_TOKEN", env("GITHUB_TOKEN")
    }
}
```

### 2.3 扩展技能系统 (Skills)

技能 (Skills) 是一种更高层的抽象结构。
- **提示词技能 (Prompt Skills)**: 封装了复杂的 `systemPrompt` 后台逻辑设定，并在编译时动态注入到对话上下文中。
- **逻辑技能 (Logic Skills)**: 封装了复杂的 Groovy `execute` 执行闭包逻辑。

要添加全局技能供所有脚本进行引用 (`include`)，必须在脚本编译前通过 Java API 注册到 `AgentRegistry`：
```java
agentRegistry.registerSkill(mySkillSpec);
```

---

## 3. 安全架构设计 (The Sandbox)

由于 AgentDSL 使用了基于 Groovy 的动态解析底层，必须纳入严格的安全沙箱机制，以防止存在恶意意图或误操作的脚本对宿主系统造成危害。

*   **无文件系统访问权**: `java.io.*` 和 `java.nio.file.*` 在系统 AST 级别的自定义配置中被显式禁用了。
*   **无系统进程权**: `java.lang.System`, `Runtime` 和 `ProcessBuilder` 被彻底拉黑。
*   **无网络层访问权**: 在直接的原始脚本执行流中，底层的 `java.net.*` 网络套接字和 HTTP 请求同样是被禁止的。
*   **集中管控原则**: 任何需要操作网络或是读取系统文件的工具，**必须**在 Java 宿主代码本地侧定义完成，或者作为外部独立的 MCP 服务器挂载。这种边界设计迫使必须的权限审计仅发生在 Java 本地服务层而非多变的脚本层。

## 4. 未来路线图规划

请参阅 `AgentDSL 迭代开发计划2.0.md` 中关于架构即将到来的更新路线图细节。其中包括了对 Project Loom(虚拟线程、高并发工作流) 体系的集成，以及面向 Autonomous Agent 生成的自主循环计划机制升级。
