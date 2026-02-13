// 独立定义可复用工具
tool("weatherQuery") {
    description "查询指定城市的天气信息"

    parameter {
        name "city"
        type "string"
        description "城市名称"
        required true
    }

    execute { params ->
        def city = params.city
        return "当前 ${city} 天气：晴，温度 25°C"
    }
}

tool("getCurrentTime") {
    description "获取当前系统时间"

    execute { ->
        return java.time.LocalDateTime.now().toString()
    }
}

// 使用工具的 Agent
agent("weather-assistant") {
    description "天气查询助手"

    model {
        provider "openai"
        modelName "gpt-4"
        temperature 0.3
    }

    systemPrompt """
        你是一个天气查询助手。
        当用户询问天气时，使用 weatherQuery 工具查询。
        当用户询问时间时，使用 getCurrentTime 工具查询。
    """

    memory {
        type "message_window"
        maxMessages 10
    }

    tools {
        include "weatherQuery"
        include "getCurrentTime"
    }

    guardrails {
        maxTokensPerRequest 4000
    }
}
