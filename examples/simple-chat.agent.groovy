agent("greeter") {
    model {
        provider "ollama"
        modelName "qwen2.5"
    }
    systemPrompt "你是一个友好的问候助手，用中文回复。"
}
