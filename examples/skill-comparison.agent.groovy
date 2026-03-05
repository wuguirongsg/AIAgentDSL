// examples/skill-comparison.agent.groovy
// 演示声明式（Prompt）和指令式（Logic）技能的区别

// 1. Prompt Skill （描述型）
// 不涉及真实的代码执行，只通过 system prompt 改变 LLM 的角色和行为方式
skill('json-formatter') {
    type 'prompt'
    description '当需要将任何文本转换为标准 JSON 格式时，调用此技能'
    instruction '''你是一个严格的数据抽取机器。请将用户的所有输入转换为 JSON 格式返回，只返回代码块。'''
}

// 2. Logic Skill （逻辑型）
// 涉及真实的系统资源调用或复杂的本地循环计算，不仅限于单次 LLM 推理
skill('weather-api-logic') {
    type 'logic'
    description '当需要查询真实天气信息时，调用此技能'

    parameter {
        name 'location'
        type 'string'
        description '想查询的城市或地区'
        required true
    }

    execute { params ->
        def loc = params.location
        println "查询天气：${loc}"
        // 实际场景：调用 HTTP API
        def response = toolCall('http_get', [url: "http://apis.juhe.cn/simpleWeather/query?format=2&city=${loc}&key=${System.getenv('JUHE_API_KEY')}"])
        return response

    // 模拟真实查询
    // if (loc.contains('北京')) {
    //     return '{\"temperature\": 22, \"condition\": \"晴天\"}'
    // }
    // return '{\"temperature\": 15, \"condition\": \"多云\"}'
    }
}

agent('hybrid-assistant') {
    description '混合型助手演示'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    systemPrompt '你是一个能够灵活运用技能的助手。根据用户的需求智能选择合适的技能。'

    skills {
        include 'json-formatter'
        include 'weather-api-logic'
    }
}
