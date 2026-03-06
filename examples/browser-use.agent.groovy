// examples/browser-use.agent.groovy
agent('web-operator') {
    description '能操控浏览器的智能体'
    // 按需配置大模型，能够配合 Browser Use 的一般都是多模态大模型或者逻辑强大的模型
    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.0
    }

    browser_use {
        sandbox false
        // 关键的操作会被拦截询问是否确认 (NativeBrowserTool 的方法名)
        hitl_on 'click', 'fill', 'navigate'
    }

    systemPrompt '''你是一个能够浏览网页并操作浏览器的全能助手。
                    请使用提供的原生浏览器操作工具完成用户的指令。'''
}

// 运行示例：
// bin/agentdsl.sh run examples/browser-use.agent.groovy --chat "帮我打开百度网盘，搜索'AgentDSL'"
