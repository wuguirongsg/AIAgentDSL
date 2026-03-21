// ═══════════════════════════════════════════════════════════════
// 示例：自主 Agent — smart preset（四阶段 Pipeline）
// ═══════════════════════════════════════════════════════════════
//
// Phase 1: LLM 问题解构（失败时自动降级）
// Phase 2: ToT 多候选策略 + 评分
// Phase 4: 元认知监控（停滞 / 置信度 / Token&时间预算等）
//
// 使用方式（需配置模型与网络环境，与 autonomous-agent 相同）:
//
//   agentdsl run examples/autonomous-smart.agent.groovy \
//       --agent SmartAgent --autonomous "复杂多步骤任务描述…"
//

agent('SmartAgent') {
    model {
        // provider 'gemini'
        // modelName 'gemini-2.5-flash'
        provider 'gemini'
        modelName 'gemini-3.1-pro-preview'
        maxTokens 8192
    }

    autonomous {
        execution_mode 'plan'
        max_steps 20
        preset 'smart'
        max_token_budget 100000
        max_time_ms 600000
    }

    tools {
        include 'http_get'
        include 'json_parse'
        include 'file_read'
        include 'file_write'
        include 'web_search'
        include 'cmd_execute'
        include 'groovy_execute'
        include 'shell_script_run'
        include 'python_run'
    }

    search {
        provider 'tavily'
        apiKey env('TAVILY_API_KEY')
    }

    // 若需联网搜索，可参照 examples/autonomous-agent.agent.groovy 增加 search { ... apiKey env('TAVILY_API_KEY') }

    systemPrompt '''你是自主任务助手。遵循 System Prompt 中注入的成功标准与策略步骤；
遇到元认知干预提示时，按要求切换思路、压缩上下文或收敛结论。'''
}

// example:
// export TAVILY_API_KEY=your api key
// export GITHUB_API_KEY=your api key
// shell/agentdsl.sh run examples/autonomous-smart.agent.groovy --agent SmartAgent --autonomous "去github上找一下OpenCode的代码，然后下载下来，然后分析一下代码，看下他的agent是如何设计，有什么设计上的特点和值得借鉴的地方"