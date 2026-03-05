# Changelog

All notable changes to the AgentDSL project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Core DSL**: Initial implementation of the `agent`, `tool`, and `workflow` blocks.
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
