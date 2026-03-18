# Contributing to AgentDSL

We're thrilled that you'd like to contribute to AgentDSL! This guide will help you get started with contributing to our project.

## Code of Conduct

By participating in this project, you agree to abide by our Code of Conduct. Please treat everyone with respect and kindness.

## How Can I Contribute?

### 1. Reporting Bugs

If you find a bug, please create an issue on GitHub. Before creating a new issue, please check if one already exists.

When creating an issue, include:
*   A clear and descriptive title.
*   Steps to reproduce the issue.
*   The exact error message or stack trace.
*   Your environment (OS, Java version, Gradle version).

### 2. Suggesting Enhancements

We welcome suggestions for new features or improvements.
If you have an idea, please open an issue and use the `enhancement` label. Describe the feature, why it would be useful, and providing a code example is a big plus.

### 3. Submitting Pull Requests

1.  **Fork the repository** to your own GitHub account.
2.  **Clone the project** to your local machine.
3.  **Create a new branch** for your feature or bug fix (`git checkout -b feature/your-feature-name` or `fix/your-bug-fix`).
4.  **Make your changes** and commit them with clear, descriptive commit messages.
5.  **Write tests** for your changes. We strive for high test coverage.
6.  **Run the test suite** locally `./gradlew test` to ensure everything passes.
7.  **Push your branch** to your fork.
8.  **Open a Pull Request** against the `main` branch of the AgentDSL repository.

## Development Setup

AgentDSL requires **Java 17**. We use Gradle for building the project.

```bash
# Clone the repo
git clone https://github.com/wuguirongsg/AgentDSL.git
cd AgentDSL

# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run CLI with a specific agent script
./gradlew :agentdsl-cli:run --args="run your-agent.groovy --chat 'Hello'"
```

## Project Structure

AgentDSL is organized as a Gradle multi-module project:

| Module | Description |
|--------|-------------|
| `agentdsl-core` | The DSL engine, AST definitions, AST validators, and core interfaces. |
| `agentdsl-compiler` | Validates and compiles `.agent.groovy` scripts into executable AST. |
| `agentdsl-runtime` | The execution engine managing agents, workflows, and tool execution. |
| `agentdsl-langchain4j` | Bridges DSL concepts to LangChain4j for LLM provider integration. |
| `agentdsl-mcp` | Model Context Protocol client implementation for external MCP server integration. |
| `agentdsl-tools` | Built-in tools (Excel, PDF, JDBC, Web Search, Browser Automation) and `@AgentTool` annotation scanner. |
| `agentdsl-cli` | The command-line interface with `run`, `list`, and `validate` commands. |

## Key Features

- **MCP Integration**: Mount external MCP servers (GitHub, Slack, etc.) as native Java tools
- **Autonomous Agent**: "Intent -> Planning -> Execution -> Observation" autonomous loop
- **Skills System**: Both prompt-based and logic-based skills for flexible agent behavior
- **Sandbox Security**: Prevent unauthorized system calls, environment variable leaks, file I/O, and thread creation
- **Observability**: Full-lifecycle debugging with `--debug` flag

## Coding Standards

- Follow standard Java coding conventions
- Use meaningful variable and method names
- Write unit tests for new features
- Ensure code is cleanly formatted (Gradle formatting is automatically applied)
- For Groovy DSL internals, favor readability and Groovy idiomatic style

## Running Examples

Check the `examples` directory for sample agent scripts:

```bash
./gradlew :agentdsl-cli:run --args="run examples/hello.agent.groovy --chat 'Hello'"
```

## Documentation

- [Language Specification](doc/lang-spec/AIAgentDSL-Language-Spec-v1.4.md)
- [User Guide (Chinese)](doc/User_Guide_zh-CN.md)
- [Architecture Guide](doc/Architecture_Guide.md)

Thank you for contributing!
