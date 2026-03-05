# AgentDSL

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/your-org/AgentDSL/actions/workflows/ci.yml/badge.svg)](https://github.com/your-org/AgentDSL/actions/workflows/ci.yml)

> [!NOTE]
> 🇨🇳 想要查看中文文档？请点击 [README_zh-CN.md](README_zh-CN.md)

**AgentDSL** is an enterprise-grade, highly-engineered Domain Specific Language (DSL) for building AI Agents in the Java ecosystem. It bridges the gap between complex AI logic and robust enterprise application development, bringing the power of frameworks like LangChain/CrewAI to Java with type safety, modularity, and observability.

### Why AgentDSL?
While 90% of AI innovation happens in Python, Java developers (especially in Enterprise/SaaS) need a DSL that fits Java engineering habits. AgentDSL is designed to reduce development workload by up to 60% compared to writing pure Java LLM integrations (like LangChain4j).

### 🚀 Core Features
1. **Model Context Protocol (MCP) First**: Seamlessly interact with the MCP ecosystem. If there's an MCP Server (like GitHub, Slack, Playwright), AgentDSL can directly mount and use it.
2. **Deterministic Logic Skills**: Not just chatting. AgentDSL emphasizes "Logic Skills" alongside "Prompt Skills", ensuring complex business flows execute with 100% accuracy.
3. **Enterprise-Grade Sandbox**: Built on Groovy with a secure sandbox runtime, preventing unauthorized system calls or environment variable access.
4. **Rich Tool Ecosystem**: Native support for LangChain4j tools, Workflows, and upcoming features like Computer Use and Data extensions.

### ⚡ 5-Minute Quick Start

**1. Create an Agent Script (`hello.agent.groovy`):**
```groovy
agent("hello-bot") {
    model { 
        provider "ollama" 
        modelName "qwen3:4b" 
    }
    systemPrompt "You are a helpful assistant."
}
```

**2. Run with the CLI:**
```bash
# Make sure Ollama and the model are running locally
export GEMINI_API_KEY="your-key-if-using-gemini"

./gradlew :agentdsl-cli:run --args="run hello.agent.groovy --message 'Hello, who are you?'"
```

### 📚 Documentation
- [DSL Syntax Manual](doc/DSL_Syntax_Manual.md) - Complete reference for `agent`, `tool`, `workflow`, and `skill`.
- [Architecture & Extension Guide](doc/Architecture_Guide.md) - How to extend `ModelProvider` or `SkillStore` via SPI.

### 🤝 Contributing
Issues and Pull Requests are welcome! Before contributing, please read the [Contribution Guidelines (CONTRIBUTING.md)](CONTRIBUTING.md) and the [Changelog (CHANGELOG.md)](CHANGELOG.md).

---
*Built with ❤️ for the Java AI Ecosystem.*
