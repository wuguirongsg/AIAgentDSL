agent("greeter") {
    model {
        provider "ollama"
        modelName "qwen:0.5b-chat"
    }
    systemPrompt "你是一个友好的问候助手，用中文回复。"
}
