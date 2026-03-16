# Changelog

All notable changes to the AgentDSL project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Autonomous Agent**: Implemented "Intent -> Planning -> Execution -> Observation" autonomous loop. Agents can now autonomously decide and complete complex tasks with only a goal set by the user.
- **Workflow Tool Execution**: Workflows now support direct tool invocation and code execution, enabling complex multi-step business flows.
- **MCP Service Discovery**: Agents can autonomously discover and invoke MCP services, dynamically finding and mounting external MCP servers.
- **Network Search Tools**: Built-in real-time web search capabilities supporting Tavily, Serper, and Zhipu search APIs.
- **Browser Automation**: Native Playwright integration for browser automation tasks.
- **Skills System**: Full-featured skills system supporting both prompt-based and logic-based skills for flexible agent behavior.
- **Debug Trace**: Full-lifecycle debugging with `--debug` flag for clear reasoning-action-observation traces.
- **Core DSL**: Implementation of `agent`, `tool`, and `workflow` blocks.
- **Compiler**: AST generation, `DslCompiler`, and `DslValidator`.
- **Runtime**: `AgentExecutor`, `AgentRegistry`, and execution trace capabilities.
- **LangChain4j Bridge**: `LangChainModelFactory`, `LangChainToolBridge` linking DSL specifications to the LangChain4j runtime API.
- **MCP**: Integration with Model Context Protocol allowing mounting of external Python/Node.js MCP servers as native Java tools.
- **Tools**: Initial built-in tools system and `@AgentTool` annotation-based scanning for native Java tool integration.
- **CLI**: `ListCommand`, `RunCommand`, and `ValidateCommand` tools.
- **Safety**: Safe sandbox execution environment prohibiting `System.exit`, raw file IO, and thread creation directly from DSL scripts.

### Changed
- *(Reserved for future changes)*

### Deprecated
- *(Reserved for future deprecations)*

### Removed
- *(Reserved for future removals)*

### Fixed
- *(Reserved for future bug fixes)*

### Security
- Groovy script sandbox enforcement enabled by default.

---

## [0.1.0-SNAPSHOT] - Project Initialization

### Added
- Initial project setup with Gradle multi-module structure.
- Core DSL engine with AST definitions and core interfaces.
- LangChain4j integration module.
- MCP client implementation.
- Built-in tools and annotation scanner.
- CLI for running and validating agent scripts.
