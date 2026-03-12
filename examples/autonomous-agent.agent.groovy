// ═══════════════════════════════════════════════════════════════
// 示例：自主 Agent（Autonomous Agent）
// ═══════════════════════════════════════════════════════════════
//
// 使用方式：
//
// Plan 模式（先规划，确认后执行）:
//   agentdsl run examples/autonomous-agent.agent.groovy \
//       --agent PlanAgent --autonomous "帮我搜索 AgentDSL 项目的信息并整理"
//
// Fast 模式（直接执行）:
//   agentdsl run examples/autonomous-agent.agent.groovy \
//       --agent FastAgent --autonomous "用 HTTP 请求获取 https://httpbin.org/get 的内容"

// ──────────────────────────────────────────────────────────────
// Plan 模式示例：先生成执行计划，用户确认后再执行
// ──────────────────────────────────────────────────────────────
agent('PlanAgent') {
    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    autonomous {
        execution_mode 'plan'
        max_steps 10
    }

    tools {
        include 'http_get'
        include 'web_search'
        include 'json_parse'
        include 'json_query'
        include 'file_read'
        include 'file_write'
    }

    search {
        provider 'tavily'
        apiKey env('TAVILY_API_KEY') // Assumes you have TAVILY_API_KEY exported in your environment
    }

    systemPrompt '''你是一个自主任务助手，帮助用户完成复杂的多步骤任务。
你擅长：
- 从网络获取信息
- 整理和分析数据
- 将结果保存到文件

请根据用户的需求，自主规划并执行任务。'''
}

// ──────────────────────────────────────────────────────────────
// Fast 模式示例：跳过规划，直接开始执行
// ──────────────────────────────────────────────────────────────
agent('FastAgent') {
    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    autonomous {
        execution_mode 'fast'
        max_steps 5
    }

    tools {
        include 'http_get'
        include 'web_search'
        include 'json_parse'
    }

    systemPrompt '你是一个自主任务助手，帮助用户完成复杂的多步骤任务。'
}
