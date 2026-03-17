agent("hello") {
    model {
        provider "ollama"
        modelName "qwen3:4b"
    }
    systemPrompt "You are a friendly assistant. Reply with a greeting."
}