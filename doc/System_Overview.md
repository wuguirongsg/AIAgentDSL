# AgentDSL 系统概要设计与现状描述

**文档版本**：v1.0  
**更新时间**：2026-02-28  

---

## 1. 系统定位

**AgentDSL** 是一个旨在简化大语言模型（LLM）驱动的智能体（Agent）、工具（Tools）以及复杂工作流（Workflows）定义的领域特定语言（DSL）框架。
它允许开发者使用极具表现力的 Groovy DSL 语法，快速构建并编排 AI 应用，将繁琐的底层 API 调用转化为高层抽象的配置与组装，同时内置了严苛的编译期校验、运行时沙箱以及完善的可观测性。

## 2. 核心技术栈

- **JDK**：Java 17+（系统基于 Java 17 长期支持版构建）。
- **语言**：Groovy（作为宿主语言解析 DSL，提供强大的元编程与闭包能力）。
- **底层 AI 框架**：LangChain4j（负责标准化的 LLM 交互、对话记忆管理工具和各类提供商对接）。
- **命令行框架**：Picocli（构建极其优雅且功能完善的终端 CLI 工具）。
- **构建系统**：Gradle（完全采用 Kotlin DSL `build.gradle.kts` 进行模块化管理）。
- **日志框架**：SLF4j + Logback（支持彩色高亮控制台日志与生产级 JSON 结构化日志）。

## 3. 模块化架构

系统被清晰地划分为多个 Gradle 子模块，严格控制依赖边界：

1. **`agentdsl-core`**：系统内核。包含所有 AST（语法树）规范模型（如 `AgentSpec`, `ToolSpec`, `WorkflowSpec`），DSL 委托类（Delegates），以及基础指标收集中心（`MetricsCollector`, `ToolMetrics`）。**无任何外部业务强依赖**。
2. **`agentdsl-compiler`**：编译器。负责解析 Groovy DSL 脚本并转换为 Core 层的 Spec 模型。内置严格的 `DslValidator`（语法、语义及引用交叉验证）以及拦截恶意类加载的基于 `GroovySandbox` 的安全沙箱管理。
3. **`agentdsl-langchain4j`**：桥接层。实现了模型工厂、记忆工厂、并将 AgentDSL 的工具规范（ToolSpec）动态适配并注册为 LangChain4j 可调用的原生工具（`LangChainToolBridge`）。
4. **`agentdsl-tools`** / **`agentdsl-mcp`**：工具生态组件。包含标准内置库，并已实现接入 Model Context Protocol (MCP) 以实现调用外部 MCP 服务器（如基于 stdio 的 GitHub MCP Server 等等）。
5. **`agentdsl-runtime`**：运行时引擎。对外提供顶层 `AgentDslEngine` 对话以及 `WorkflowExecutor` 执行环境，组合 Compiler 与 Bridge 层，提供从“脚本加载”到“执行并返回结果”的完整生命周期。并负责产生 `ExecutionTrace` 与 `StepTrace` 记录。
6. **`agentdsl-cli`**：命令行控制台。将上述所有能力包装为 `agentdsl` 终端指令组合包，打包为 Fat Jar 供开发者免写 Java 代码直接使用。

## 4. 已实现核心特性 (截止至 Sprint 5)

### 4.1 灵活强大的 DSL 语法表示
- **Agent 定义**：支持声明式配置 `model` (驱动模型)、`systemPrompt` (人设指令)、`memory` (长短记忆管理类型)、`tools` (嵌入或引用工具列表)、`rag` 及内置各种策略约束。
- **独立 Tool 定义**：支持灵活的方法声明、多类型参数绑定校验（含 `min/max`, `regex` 等约束判断）、权限审查声明（`permissions`）、超时兜底与闭包逻辑注入。
- **工作流 (Workflow) 编排**：支持声明一套完整的逻辑链路，节点类型已覆盖：
  - 顺序串行 (`step`)。
  - 条件路由 (`condition` + `check` + `on`)。
  - 并发执行 (`parallel`)。
  - 次数与条件循环 (`loop` + `maxIterations` + `until`)。

### 4.2 强有力的编译期与沙箱校验
- **语法语义验证**：一旦 DSL 脚本有缺失关键属性的行为（如未指定 provider/modelName），在 `DslValidator` 层面会立刻被识别并抛出带详细错误码的编译错误。
- **引用隔离校验**：严格校验工作流中提及的 `agentRef` 和 Agent 引入的子级工具在上下文中是否存在，将低级拼写错误遏制在应用启动或 CI/CD 构建前。
- **Groovy 防御沙箱**：基于正则和黑名单拦截恶意语法组合 `System.exit`, `Runtime.exec`, 或未授权的文件读写。

### 4.3 完善的可观测性网络
- **内存无锁指标收集 (ToolMetrics)**：内置全局 `MetricsCollector`，通过 `ConcurrentLinkedDeque` 滑动窗口自动管理记录上限，提供任意工具调用的吞吐、平均耗时、成功率统计。
- **调用链路追踪 (Execution Trace)**：针对复杂的 Workflow 编排调用，执行结果中内置包含了以单 Step 为粒度的生命周期时长、异常情况、状态枚举详情 （支持向树干或 Grafana 进一步汇聚导出）。
- **结构级别日志**：同时维护控制台彩色开发者输出和 `logback-json.xml` ELK 日志搜集预配置。

### 4.4 开箱即用的 CLI 工具集
- 提供极轻量化的本地终端操作接口：
  - `agentdsl run <script> --chat "..."` (或 `--workflow` 执行整链路并用 `--trace` 输出打点详情)。
  - `agentdsl validate <script> --json` (CI/CD 流水线强无缝集成、错误验证拦截器)。
  - `agentdsl list <script>` (文本或 JSON 格式展示单脚本背后的所有定义项概览)。

## 5. 项目后续演进方向
- **Dashboard & API Server**：为引擎增加轻量级的 Restful API 入口、通过 SPI 将现有 Trace 与 Metrics 投送到可视化监控系统。
- **流式响应支撑**：在基础文本问答之上拓展支持 Reactive/SSE 流式字符响应处理。
