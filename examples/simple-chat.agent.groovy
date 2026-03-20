// AgentDSL 最简示例
// customSetting 'think', false - Ollama 专用参数，控制是否启用思考模式
// 更多 customSetting 参数参见文档：doc/User_Guide_zh-CN.md

agent('greeter') {
    model {
        //provider 'gemini'
        //modelName 'gemini-2.5-flash'
        provider 'ollama'
        modelName 'qwen3.5:latest'
        baseUrl 'http://192.168.10.131:11434'
        // Ollama 特定参数示例：
        // customSetting 'think', true        // 启用思考模式
        // customSetting 'returnThinking', true // 返回思考过程
        customSetting 'think', false         // 禁用思考模式（默认）
    }
    systemPrompt '你是一个友好的问候助手，用中文回复。'
}
