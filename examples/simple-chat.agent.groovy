agent('greeter') {
    model {
        provider 'gemini'
        modelName 'gemini-2.0-flash'
    }
    systemPrompt '你是一个友好的问候助手，用中文回复。'
}
