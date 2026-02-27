# AgentDSL 遗留问题与后续待办事项 (TODO & Backlog)

本文档记录了在 Sprint 1 ~ Sprint 5 开发过程中遗留的技术债、待优化的点，以及未来需要补充的特性。请在后续的 Sprint 中视情况安排解决。

## 1. 可观测性 (Sprint 4 遗留)

### 1.1 `StepTrace` 的覆盖范围补全
- **当前状态**：目前 `WorkflowExecutor` 中只有顺序执行步骤（`executeSequential`）包裹了计时和生成 `StepTrace`，并将结果存入 `ExecutionTrace`。
- **待办项**：需要对 `executeParallel`, `executeCondition`, `executeLoop` 等其他类型的分支节点，也完整地增加 `recordStepTrace` 的逻辑，以确保整个工作流树在追踪上的完整性。

### 1.2 对接外部监控/日志平台
- **MDC 字段扩展（日志）**：在 `WorkflowExecutor` 和 `AgentExecutor` 中，配合 `MDC.put("workflowName", ...)` 和 `MDC.put("agentName", ...)`, 让每条日志自动带上业务维度标签，方便基于工作流或 Agent 进行日志过滤（参见 `logback-json.xml` 中的 TODO [METRICS-EXT-9]）。
- **指标导出 (Micrometer/OpenTelemetry)**：`MetricsCollector` 目前是内存聚合输出。后续可以将其持有的 `ToolMetrics` 定期输出，或直接桥接给 `Micrometer MeterRegistry` 或暴露为 OpenTelemetry 的 span（TODO [METRICS-EXT-1~7]）。
- **Trace 导出 (OpenTelemetry)**：将 `ExecutionTrace` / `StepTrace` 模型桥接成 OTel 的 span 树，推送到 Jaeger/Zipkin 或 Grafana Tempo（TODO [TRACE-EXT-1~3]）。
- **指标与持久化仪表盘 (SPI 暴露)**：如果用户真正需要海量的长周期持久化仪表盘，后续计划不将其强行耦合在 core 里，而是通过 SPI (Service Provider Interface) 的方式向外暴露数据，允许外接 SQLite、Elasticsearch 或 Prometheus 等方式来消费并持久化这些数据。
- **日志持久化依赖引入**：若生产需要启用 `logback-json.xml`，需在依赖中正式引入 `net.logstash.logback:logstash-logback-encoder` 并将其加入打包范围。

## 2. CLI 与编译器增强 (Sprint 5 遗留)

### 2.1 `DslCompileResult` 诊断信息扩展
- **当前状态**：校验逻辑（如 `DslValidator`）会在发现错误时直接抛出 `DslCompilationException`。
- **待办项**：按照原 Sprint 5 设计，应在 `DslCompileResult` 中增加一个内部类 `Diagnostic`（包含警告或错误信息、行号）。这样编译器可以支持并收集多个软性警告（如“建议添加超时”），而不是一遇到错误就中断。遇到严重错误（Error）再决定是否通过校验。

### 2.2 CLI 模块集成测试
- **当前状态**：`agentdsl-cli` 模块已实现 `run`, `validate`, `list` 等命令，并在命令行下可用，但 JUnit 自动化测试覆盖不足。
- **待办项**：编写针对 `RunCommand`, `ValidateCommand`, `ListCommand` 的集成测试（利用 picocli 的测试套件捕获 System.out 和 exit code 进行断言校验）。

## 3. 其他技术债与改进

### 3.1 `WorkflowExecutorTest` 的模型硬编码问题
- **当前状态**：测试中存在大量的 `modelName "qwen:0.5b-chat"` 甚至 `gpt-4`。虽然当前已经全部替换为 `qwen:0.5b-chat` 以加速本地测试跑通，但在缺少外部依赖或离线时，调用真实 LLM 提供商会报错或超时。
- **待办项**：引入 `WireMock` 或创建一个本地 Dummy Provider 代替 Ollama/OpenAI 来专门执行这些单元测试，彻底解耦环境依赖，保证 `gradle test` 在任何环境下 30 秒内秒出结果并且百分百稳定。

### 3.2 动态挂载/卸载热加载增强
- **当前状态**：支持 `AgentDslEngine.reload()` 重新读取全部脚本。
- **待办项**：只热加载发生变化的文件，实现 Agent/Tool 的优雅上下线。

### 3.3 文档示例同步
- 每次新增特性（例如在 CLI 模块中新增的具体参数指令），需要同步更新至 `README.md` 与 `doc/AgebtDSL-Language-Spec-v1.1.md` 中。
