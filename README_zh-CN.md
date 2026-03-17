# AgentDSL (中文)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/wuguirongsg/AgentDSL/actions/workflows/ci.yml/badge.svg)](https://github.com/wuguirongsg/AgentDSL/actions/workflows/ci.yml)

> [!NOTE]
> 🌐 Looking for the English documentation? See [README.md](README.md)

**AgentDSL** 是 Java 生态下的企业级 AI Agent 领域特定语言 (DSL)。它将复杂的 AI 逻辑与健壮的企业级开发最佳实践相结合，让 Java 开发者也能像使用 Python 的 LangChain / CrewAI 一样优雅地编排 Agent，同时保持类型安全、模块化和极佳的可观测性。

### 为什么选择 AgentDSL？
在充斥着 Python AI 框架的今天，Java 开发者（尤其是企业级软件、SaaS 开发者）迫切需要一套**符合 Java 工程习惯**的标准。使用 AgentDSL，相比纯写 LangChain4j 代码可以显著减少工程代码量（在多 Agent 编排场景下通常可减少 60% 以上）。

### 🚀 核心杀手锏
1. **深度集成 MCP (Model Context Protocol)**：作为 Java 社区对接 MCP 协议最丝滑的框架之一。通过简单的 DSL 声明，即可将任意标准 MCP Server（如 GitHub, Slack）挂载为 Agent 工具。
2. **强逻辑 Skills (技能) 编排**：除了传统的 Prompt 提示词技能外，AgentDSL 引入了确定的“逻辑型技能 (Logic Skills)”，确保 Agent 不仅仅是会聊天，还能百分百准确地执行复杂的业务流程和数据流。
3. **生产级安全沙箱 (The Sandbox)**：内核内置严格的安全隔离机制，防止未授权环境访问，确保脚本执行安全。
4. **全能数据与自动化 (New v1.4.0)**：内置 Excel、PDF、JDBC 数据库、互联网实时搜索（Tavily/Serper/智谱）以及原生浏览器自动化 (Playwright) 驱动能力。
5. **自主 Agent (Autonomous)**：支持 "意图 -> 规划 -> 执行 -> 观察" 的自动循环，只需设定目标，Agent 即可自主决策并完成复杂任务。
6. **灵活的基础设施**：包含强大的 CLI 工具，提供全链路调试追踪 (`--debug`)。

### ⚡ 5 分钟快速开始

**1. 编写第一个 Agent 脚本 (`hello.agent.groovy`):**
```groovy
agent("hello-bot") {
    model { 
        provider "ollama" 
        modelName "qwen3:4b" 
    }
    systemPrompt "你是一个乐于助人的 AI 助手。"
}
```

**2. 使用 CLI 运行:**
```bash
# 确保本地已启动 Ollama 且存在 qwen3:4b 模型
# 若使用 Gemini 等在线模型，请先声明环境变量：export GEMINI_API_KEY="..."

./gradlew :agentdsl-cli:run --args="run hello.agent.groovy --chat '你好，请介绍一下你自己。'"
```

### 📚 核心文档
- [🚀 开发者入门指南](doc/User_Guide_zh-CN.md)：最通俗易懂的快速上手教程。
- [📖 AgentDSL 语言规范 v1.4.0](doc/lang-spec/AgentDSL-Language-Spec-v1.4.md)：权威的语法、关键字与语义定义标准。
- [🏗️ 架构与扩展指南](doc/Architecture_Guide_zh-CN.md)：详述如何通过 SPI 扩展自定义大模型和技能库。

### 🤝 参与共建
欢迎提出 Issue 或 Pull Request！在提交代码前，请先阅读 [贡献指南 (CONTRIBUTING.md)](CONTRIBUTING.md) 和 [开发变更日志 (CHANGELOG.md)](CHANGELOG.md)。

---
*Built with ❤️ for the Java AI Ecosystem.*
