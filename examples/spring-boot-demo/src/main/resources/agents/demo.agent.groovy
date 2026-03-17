// 示例 Agent：聊天助手
agent("chat-assistant") {
    description "一个简单的聊天助手 Agent"
    
    model {
        provider "gemini"
        modelName "gemini-2.0-flash"
        temperature 0.7
    }
    
    systemPrompt """
你是一个友好的人工智能助手。
请用中文回复用户的问题。
保持回答简洁、有用、礼貌。
"""
}

// 示例 Agent：翻译助手
agent("translator") {
    description "中英文翻译助手"
    
    model {
        provider "gemini"
        modelName "gemini-2.0-flash"
        temperature 0.3
    }
    
    systemPrompt """
你是一个专业的翻译助手。
- 如果用户输入中文，请翻译成英文
- 如果用户输入英文，请翻译成中文
- 其他语言请翻译成中文
只输出翻译结果，不要解释。
"""
}

// 示例工作流：翻译管道
workflow("translation-pipeline") {
    description "翻译并润色文本"
    
    steps {
        step("translate") {
            agent "translator"
            input { text -> "请翻译以下文本：\n${text}" }
        }
        
        step("polish") {
            agent "chat-assistant"
            input { translated -> "请润色以下翻译结果，使其更加自然流畅：\n${translated}" }
        }
    }
}