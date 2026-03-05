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

AgentDSL requires **Java 21**. We use Gradle for building the project.

```bash
# Clone the repo
git clone https://github.com/your-org/AgentDSL.git
cd AgentDSL

# Build the project
./gradlew build

# Run all tests
./gradlew test
```

## Anatomy of the Project

*   `agentdsl-core`: The DSL engine, AST definitions, and core interfaces.
*   `agentdsl-compiler`: Validates and compiles the `.agent.groovy` scripts.
*   `agentdsl-runtime`: The execution engine managing agents and workflows.
*   `agentdsl-langchain4j`: Bridges the DSL concepts to LangChain4j.
*   `agentdsl-mcp`: Model Context Protocol client implementation.
*   `agentdsl-tools`: Built-in tools and annotation scanner.
*   `agentdsl-cli`: The command-line interface.

## Style Guide

We generally follow standard Java coding conventions. Please ensure your code is cleanly formatted and well-documented. For Groovy code (the DSL internals), favor readability and Groovy idiomatic style where appropriate.

Thank you for contributing!
