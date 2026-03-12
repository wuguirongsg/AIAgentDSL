// ═══════════════════════════════════════════════════════════════
// 示例：自主模式 + 动态发现 MCP
// ═══════════════════════════════════════════════════════════════
//
// 目标：
// 1) 当模型尝试调用一个当前未注册的工具时
// 2) 运行时自动去 mcp.so 搜索候选 MCP Server
// 3) 动态挂载后继续执行任务
//
// 使用方式（自主模式）：
//   shell/agentdsl.sh run examples/autonomous-mcp-discovery.agent.groovy \
//      --agent auto-mcp-agent \
//      --autonomous "请帮我查询杭州今天天气，并给出穿衣建议"
//
// 说明：
// - 该示例依赖外网访问 mcp.so。
// - 动态发现是兜底机制：仅在工具缺失时触发。
// - 当前实现有安全白名单限制，只允许受限命令格式。

agent('auto-mcp-agent') {
    description '自主执行 Agent，遇到缺失工具时自动发现并挂载 MCP'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    autonomous {
        execution_mode 'fast'
        max_steps 6
    }

    // 开启动态发现 MCP（关键配置）
    auto_discover_mcp true
    mcp_registry 'mcp.so'

    systemPrompt '''
你是一个自主执行助手。

执行原则：
1. 优先直接完成用户目标。
2. 当你需要调用工具但当前工具集中不存在时，允许触发动态 MCP 发现机制。
3. 成功拿到工具结果后，再给出简洁中文结论。

对于“天气/地图/GitHub 查询”类任务，请优先尝试工具调用；若工具暂不可用，继续尝试并等待系统自动发现可用 MCP。
'''
}

// 运行示例：
// shell/agentdsl.sh run examples/autonomous-mcp-discovery.agent.groovy --agent auto-mcp-agent --autonomous "请帮我查询杭州今天天气，并给出穿衣建议"

