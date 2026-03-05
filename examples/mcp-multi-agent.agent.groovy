// examples/mcp-multi-agent.agent.groovy
// 演示结合多 Agent 工作流与实际可使用的 MCP 协议服务器的高级场景
// 此脚本已被修改为完全可运行版本，使用了标准的官方 server-filesystem 提取本地文件内容

agent('file-analyst') {
    description '本地文件分析专员'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    systemPrompt '你是一位专业的文件审阅分析师。你需要熟练使用文件读取工具（MCP 提供）来读取本地文件的内容。获取内容后快速提炼核心要点。'

    // 挂载一个无需 API Key 且立即可用的标准 Node MCP 文件服务器
    // 允许该服务器访问 /tmp 目录
    mcp {
        server('filesystem-server') {
            transport 'stdio'
            timeout 60 // 给 npx 下载和初始化留出时间
            // npx 将随时自动下载并运行该 MCP
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
