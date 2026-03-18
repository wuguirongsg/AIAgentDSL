# AIAgentDSL Architecture & Extension Guide

> **Document Version**: v1.0
> **Target Audience**: Core developers, Contributors, System Architects

## 1. System Architecture Overview

AIAgentDSL is designed as a modular, extensible, and type-safe framework for building AI Agents in Java. The core philosophy is to provide a declarative DSL on top while maintaining strict engineering standards underneath.

### 1.1 Core Modules

The project is structured into several highly cohesive modules:

*   **`agentdsl-core`**: The foundation. Contains the DSL engine (based on Groovy), Abstract Syntax Tree (AST) definitions (`spec` models like `AgentSpec`, `ToolSpec`), and the core compiler/validator interfaces.
    *   *Key Classes*: `AgentDslEngine`, `DslBaseScript`, POJOs under `com.agentdsl.core.spec`.
*   **`agentdsl-compiler`**: Responsible for parsing the `.agent.groovy` code into the AST models defined in `core`. It performs structural and semantic validation before execution.
    *   *Key Classes*: `DslCompiler`, `DslValidator`.
*   **`agentdsl-langchain4j`**: The bridge between the declarative models and the underlying LangChain4j implementation. It converts configurations (like `ModelSpec`) into executable LangChain4j objects (`ChatLanguageModel`, `ToolSpecification`).
    *   *Key Classes*: `LangChainModelFactory`, `LangChainToolBridge`.
*   **`agentdsl-mcp`**: Implements the Model Context Protocol (MCP) client. This is a critical module that allows AgentDSL to mount standard MCP Servers as native tools.
    *   *Key Classes*: `McpClientFactory`, `McpToolProviderBridge`.
*   **`agentdsl-tools`**: A built-in library of tools (e.g., HTTP, Database, Excel, File IO). It also provides the `@AgentTool` annotation-based mechanism for scanning tools from user code.
    *   *Key Classes*: `BuiltinToolRegistry`, `ToolScanner`.
*   **`agentdsl-runtime`**: The execution engine. It manages the lifecycle of Agents, executes the ReAct or Custom loops, and manages the state/memory.
    *   *Key Classes*: `AgentExecutor`, `AgentRegistry`, `WorkflowExecutor`.
*   **`agentdsl-cli`**: A command-line interface for interacting with the engine seamlessly.
    *   *Key Classes*: `RunCommand`, `ListCommand`, `ValidateCommand`.

### 1.2 The Execution Pipeline

1.  **Parsing Phase**: `agentdsl-core` and `agentdsl-compiler` parse the script and build `AgentSpec`, `ToolSpec`, `SkillSpec`, etc.
2.  **Validation Phase**: The AST is validated for required fields, logical correctness, and sandbox constraints.
3.  **Registration Phase**: The validated specs are registered into the `AgentRegistry`. Skills are resolved and flattened into `ToolSpec` objects.
4.  **Creation Phase**: When an agent is invoked, `agentdsl-langchain4j` converts the specifications into concrete LangChain4j `AiServices`.
5.  **Execution Phase**: The `agentdsl-runtime` routes the execution through the appropriate executor (Standard, Workflow, or Autonomous).

---

## 2. SPI Extension Guide

AIAgentDSL is built heavily on the Service Provider Interface (SPI) pattern, allowing enterprise users to inject custom logic without modifying the core framework.

### 2.1 Extending the Model Provider

To add support for a new LLM provider (e.g., a localized, proprietary model), you need to implement a parser for the `model {}` block.

1.  **Implement the factory interface** (usually extending a base implementation in the LangChain bridge).
2.  Your factory should inspect the `ModelSpec.provider` field (e.g., `"my-custom-llm"`).
3.  If it matches, parse the `baseUrl`, `apiKey`, and custom parameters, and return a LangChain4j `ChatLanguageModel` instance.
4.  Register your factory via Java SPI (`META-INF/services/your.factory.Interface`).

### 2.2 Extending the Tool System

There are three ways to provide custom tools:

#### A. DSL `tool` block
Define tools inline in the `.agent.groovy` file using closures. This is best for lightweight, script-specific logic.

#### B. Annotation Scanning (`@AgentTool`)
Best for writing robust, type-safe Java code.

```java
public class MyCompanyTools {
    @AgentTool(name = "query_user", description = "Query user details from the internal CRM.")
    public String queryUser(@ToolParam(description = "User ID") String userId) {
        // Your existing Java business logic here
        return crmClient.getUser(userId).toJson();
    }
}
```
You then register this object with the engine, and the `ToolScanner` handles the translation to LangChain4j format.

#### C. MCP Protocol (The Recommended Ecosystem Approach)
If your tools are written in Python, Node.js, or you want to use existing community tools (like Playwright, GitHub, Postgres), you don't write Java code at all.

Provide the external tool as an MCP server, and mount it in the DSL:
```groovy
mcp {
    server("github") {
        transport "stdio"
        command "npx", "-y", "@modelcontextprotocol/server-github"
        env "GITHUB_TOKEN", env("GITHUB_TOKEN")
    }
}
```

### 2.3 Extending Skills (Prompt & Logic)

Skills are higher-level constructs.
- **Prompt Skills**: Encapsulate complex `systemPrompt` logic and are injected into the context.
- **Logic Skills**: Encapsulate complex Groovy `execute` closures.

To add global skills that all scripts can use (`include`), they must be registered in the `AgentRegistry` before script compilation via Java API:
```java
agentRegistry.registerSkill(mySkillSpec);
```

---

## 3. Security Architecture (The Sandbox)

Because the DSL is Groovy-based, AgentDSL incorporates a strict security sandbox to prevent malicious or accidental system harm.

*   **No File System Access**: `java.io.*` and `java.nio.file.*` are blacklisted in the AST customizer.
*   **No Processes**: `java.lang.System`, `Runtime`, and `ProcessBuilder` are blocked.
*   **No Networking**: `java.net.*` sockets and HTTP connections are forbidden from within the raw script logic. 
*   **Controlled Execution**: Tools that require networking or file access should be defined as Java native tools or MCP servers, enforcing a boundary where security audits can be performed on the Java side rather than the script side.

## 4. Future Roadmap

See the `AIAgentDSL 迭代开发计划2.0.md` for upcoming architectural changes, including Project Loom (Virtual Threads) integration for high concurrency workflows and Autonomous Agent planning loops.
