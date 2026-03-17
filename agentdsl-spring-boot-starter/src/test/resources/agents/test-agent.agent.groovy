agent("test-agent") {
    model {
        provider "gemini"
        modelName "gemini-2.0-flash"
    }
    systemPrompt "You are a test assistant for Spring Boot integration testing."
}

workflow("test-workflow") {
    description "A simple test workflow"
    steps {
        step("greet") {
            agent "test-agent"
            input { msg -> "Please greet: " + msg }
        }
    }
}