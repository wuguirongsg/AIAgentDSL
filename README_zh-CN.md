# AgentDSL (中文)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/your-org/AgentDSL/actions/workflows/ci.yml/badge.svg)](https://github.com/your-org/AgentDSL/actions/workflows/ci.yml)

> [!NOTE]
> 🌐 Looking for the English documentation? See [README.md](README.md)

**AgentDSL** 是 Java 生态下的企业级 AI Agent 领域特定语言 (DSL)。它将复杂的 AI 逻辑与健壮的企业级开发最佳实践相结合，让 Java 开发者也能像使用 Python 的 LangChain / CrewAI 一样优雅地编排 Agent，同时保持类型安全、模块化和极佳的可观测性。

### 为什么选择 AgentDSL？
在充斥着 Python AI 框架的今天，Java 开发者（尤其是企业级软件、SaaS 开发者）迫切需要一套**符合 Java 工程习惯**的标准。使用 AgentDSL，相比纯写 LangChain4j 代码可以减少 60% 以上的工程代码量。

### 🚀 核心杀手锏
1. **深度集成 MCP (Model Context Protocol)**：作为 Java 社区对接 MCP 协议最丝滑的框架之一。通过简单的 DSL 声明，即可将任意标准 MCP Server（如 GitHub, Slack）挂载为 Agent 工具。
2. **强逻辑 Skills (技能) 编排**：除了传统的 Prompt 提示词技能外，AgentDSL 引入了确定的“逻辑型技能 (Logic Skills)”，确保 Agent 不仅仅是会聊天，还能百分百准确地执行复杂的业务流程和数据流。
3. **生产级安全沙箱 (The Sandbox)**：基于 Groovy 实现，内置严格的安全沙箱机制。通过拦截 `System.exit()` 和未授权环境访问，企业用户可以放心执行动态的 Agent 脚本。
4. **灵活的基础设施**：包含强大的 CLI 工具，同时提供完整的 API 供 Spring Boot 等框架集成。

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

./gradlew :agentdsl-cli:run --args="run hello.agent.groovy --message '你好，请介绍一下你自己。'"
```

### 📚 核心文档
- [📖 DSL 语法手册](doc/DSL_Syntax_Manual_zh-CN.md)：覆盖 `agent`, `tool`, `workflow`, `skill` 所有属性的详细说明。
- [🏗️ 架构与扩展指南](doc/Architecture_Guide_zh-CN.md)：详述模块依赖关系，以及如何通过 SPI 扩展自定义大模型和技能库。

### 🤝 参与共建
欢迎提出 Issue 或 Pull Request！在提交代码前，请先阅读 [贡献指南 (CONTRIBUTING.md)](CONTRIBUTING.md) 和 [开发变更日志 (CHANGELOG.md)](CHANGELOG.md)。

---
*Built with ❤️ for the Java AI Ecosystem.*
