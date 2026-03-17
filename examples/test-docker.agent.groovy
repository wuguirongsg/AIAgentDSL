agent("test-docker") {
    model {
        provider "ollama"
        modelName "qwen3:4b"
    }
    systemPrompt "You are a test agent."
}