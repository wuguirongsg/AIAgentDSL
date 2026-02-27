/**
 * MCP GitHub Server 示例。
 * 演示如何通过 DSL 对接 MCP Server，让 Agent 获得 GitHub API 工具能力。
 *
 * 前提条件：
 * 1. 已安装 Node.js 和 npx
 * 2. 设置环境变量 GITHUB_TOKEN
 *
 * 使用方式：
 *   engine.loadDsl(new File("examples/mcp-github.agent.groovy"))
 *   engine.chat("github-assistant", "列出仓库 langchain4j/langchain4j 的最近 5 个 issues")
 */

// 定义一个对接 GitHub MCP Server 的 Agent
agent('github-assistant') {
    description 'GitHub 智能助手 - 通过 MCP 协议访问 GitHub API'

    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }

    systemPrompt '''你是一个 GitHub 助手。
你可以通过工具来查询 GitHub 上的仓库、Issues、Pull Requests 等信息。
请用中文回答用户的问题，并在回答中引用具体的数据。
如果工具调用失败，请告知用户并给出可能的原因。'''

    memory {
        type 'window'
        maxMessages 20
    }

    // MCP 对接 GitHub Server
    mcp {
        server('github') {
            transport 'stdio'
            command 'npx', '-y', '@modelcontextprotocol/server-github'
            env 'GITHUB_TOKEN', System.getenv('GITHUB_TOKEN') ?: 'your-token-here'
            timeout 30
            logEvents false
        }
    }
}

// 也可以同时对接多个 MCP Server
agent('multi-tool-assistant') {
    description '多工具助手 - 同时使用 GitHub 和文件系统 MCP Server'

    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }

    systemPrompt '''你是一个多功能助手，可以同时访问 GitHub 和本地文件系统。
请根据用户的需求选择合适的工具来完成任务。'''

    mcp {
        // GitHub MCP Server
        server('github') {
            transport 'stdio'
            command 'npx', '-y', '@modelcontextprotocol/server-github'
            env 'GITHUB_TOKEN', System.getenv('GITHUB_TOKEN') ?: 'your-token-here'
        }

        // 文件系统 MCP Server
        server('filesystem') {
            transport 'stdio'
            command 'npx', '-y', '@modelcontextprotocol/server-filesystem', '/tmp'
        }

    // 只暴露指定工具（可选）
    // filterTools "search_repositories", "list_issues", "read_file", "list_directory"
    }
}
