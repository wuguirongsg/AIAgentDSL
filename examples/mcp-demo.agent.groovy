/**
 * MCP 示例合集
 * 演示了如何对接不同的 MCP Server，包括：
 * - AMap (高德地图) MCP Server
 * - GitHub MCP Server
 * - ModelScope MCP Server
 * - 文件系统 MCP Server (本地文件分析报告流)
 */

// ==========================================
// AMap (高德地图) MCP 示例
// ==========================================
// 运行示例：
// export AMAP_MAPS_API_KEY="您的真实高德API_KEY"
// bin/agentdsl.sh run examples/mcp-demo.agent.groovy --agent amap-navigator --chat "帮我规划一下从北京西站到故宫博物院的地铁路线"
agent('amap-navigator') {
    description '高德地图智能体，能够根据用户指令查询地理信息、路线等。'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.1
    }

    mcp {
        server('amap-maps') {
            command 'npx', '-y', '@amap/amap-maps-mcp-server'
        // 注意：请确保运行环境（或在运行脚本前）以环境变量方式注入了 AMAP_MAPS_API_KEY
        }
    }

    systemPrompt '''你是一个专业的出行和地理信息助手。
请使用高德地图 (Amap Maps) 提供的 MCP 工具，辅助用户完成地点查询、路线规划等地理相关的请求。
如果请求失败，请友好地提示用户检查 AMAP_MAPS_API_KEY 是否设置或配额是否充足。'''
}


// ==========================================
// GitHub MCP 示例
// ==========================================
// 运行示例:
// export GITHUB_TOKEN="您的真实GitHub_Token"
// bin/agentdsl.sh run examples/mcp-demo.agent.groovy --agent github-assistant --chat "列出仓库 langchain4j/langchain4j 的最近 5 个 issues"
agent('github-assistant') {
    description 'GitHub 智能助手 - 通过 MCP 协议访问 GitHub API'

    model {
        provider 'ollama'
        modelName 'qwen:0.5b-chat'
    }

    systemPrompt '''你是一个 GitHub 助手。
你可以通过工具来查询 GitHub 上的仓库、Issues、Pull Requests 等信息。
请用中文回答用户的问题，并在回答中引用具体的数据。
如果工具调用失败，请告知用户并给出可能的原因。'''

    memory {
        type 'window'
        maxMessages 20
    }

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
        modelName 'qwen:0.5b-chat'
    }

    systemPrompt '''你是一个多功能助手，可以同时访问 GitHub 和本地文件系统。
请根据用户的需求选择合适的工具来完成任务。'''

    mcp {
        server('github') {
            transport 'stdio'
            command 'npx', '-y', '@modelcontextprotocol/server-github'
            env 'GITHUB_TOKEN', System.getenv('GITHUB_TOKEN') ?: 'your-token-here'
        }
        server('filesystem') {
            transport 'stdio'
            command 'npx', '-y', '@modelcontextprotocol/server-filesystem', '/tmp'
        }
    }
}


// ==========================================
// ModelScope Hub MCP 示例
// ==========================================
// 运行示例（记得先替换脚本中的 url 为真实地址）：
// bin/agentdsl.sh run examples/mcp-demo.agent.groovy --agent modelscope-assistant --chat "查一下有哪些可以查询地图的mcp服务"
agent('modelscope-assistant') {
    description 'ModelScope 接口助手，能够使用 SSE MCP 与 ModelScope Hub API 进行交互。'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.1
    }

    mcp {
        // SSE 类型的 MCP 远程服务器
        server('modelscope_hub_api') {
            transport 'sse'
            url 'https://mcp.api-inference.modelscope.net/<你的UUID>/sse'
            timeout 60
        }
    }

    systemPrompt '''你是一个专门辅助工程师调用和查询 ModelScope Hub 的助手。
请使用通过 SSE MCP 提供给你的工具来完成用户的相关请求，例如查询模型信息、获取下载链接等。
如果接口返回异常，请友好地提示用户检查配置的 UUID 是否正确以及网络是否通畅。'''
}


// ==========================================
// 多Agent工作流 + 文件系统 MCP 综合示例
// ==========================================
// 运行示例:
// bin/agentdsl.sh run examples/mcp-demo.agent.groovy --workflow file-analysis-report --input "请分析一下当前项目的目录结构"
agent('file-analyst') {
    description '本地文件分析专员'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    systemPrompt '你是一位专业的文件审阅分析师。你需要熟练使用文件读取工具（MCP 提供）来读取本地文件的内容。获取内容后快速提炼核心要点。'

    mcp {
        server('filesystem-server') {
            transport 'stdio'
            timeout 60
            command 'npx', '-y', '@modelcontextprotocol/server-filesystem', './skills'
        }
    }
}

agent('report-writer') {
    description '分析报告主笔'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    systemPrompt '你是一位资深的技术作家。根据文件分析师提供的原始结论和特征，撰写格式清晰、有说服力的深度分析报告。'
}

workflow('file-analysis-report') {
    description '文件调研与分析报告流'

    steps {
        step('analyze') {
            agent 'file-analyst'
            input { ctx -> "请使用MCP文件工具读取 skills 目录下的文件内容，然后根据以下用户需求进行分析：\n\n${ctx}" }
        }

        step('draft') {
            agent 'report-writer'
            input { analysisData -> "基于文件分析师提取的以下材料，请撰写一份格式清晰的结论简报：\n\n${analysisData}" }
            output { report -> report }
        }
    }
}
