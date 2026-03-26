/**
 * 工具与数据处理 (Tools & Data) 示例合集
 * 演示了 AgentDSL 中各种内置工具和外部数据交互的使用方式：
 *
 * 1. 基础内置与自定义工具 (HTTP, JSON, File, 自定义 Groovy 工具)
 * 2. 浏览器自动化控制 (Browser Use)
 * 3. 代码执行器 (Groovy, Python, Shell)
 * 4. 视觉与多模态分析 (Vision)
 * 5. 网络搜索 (Web Search)
 * 6. 办公文档处理 (Word 读写)
 * 7. 外部数据源与报表生成 (API 数据拉取与分析)
 */

// ==========================================
// 1. 基础内置与自定义工具
// ==========================================

// --- 独立定义可复用自定义工具 ---
tool('weatherQuery') {
    description '查询指定城市的天气信息'
    parameter { name 'city'; type 'string'; description '城市名称'; required true }
    execute { params -> return "当前 ${params.city} 天气：晴，温度 25°C" }
}

tool('getCurrentTime') {
    description '获取当前系统时间'
    execute { -> return java.time.LocalDateTime.now().toString() }
}

// 运行示例: bin/agentdsl.sh run examples/tools-demo.agent.groovy --agent weather-assistant --chat "今天北京天气如何？"
agent('weather-assistant') {
    description '天气查询助手 (自定义工具示例)'
    model { provider 'qwen'; modelName 'qwen3.5-plus'; temperature 0.3 }
    systemPrompt '''你是一个天气查询助手。
当用户询问天气时，使用 weatherQuery 工具查询。
当用户询问时间时，使用 getCurrentTime 工具查询。'''
    memory { type 'message_window'; maxMessages 10 }
    tools { include 'weatherQuery'; include 'getCurrentTime' }
    guardrails { maxTokensPerRequest 4000 }
}

// 运行示例: bin/agentdsl.sh run examples/tools-demo.agent.groovy --agent tools-demo --chat "帮我算一下 15 乘以 24，然后再读取 /tmp/test.txt 的内容"
agent('tools-demo') {
    description '内置工具增强演示 Agent'
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '''你是一个全能助手，可以：
1. 使用 http_get / http_post 发送 HTTP 请求
2. 使用 json_parse / json_query 处理 JSON 数据
3. 使用 file_read / file_write 读写文件（仅限 /tmp 目录）
请根据用户需求选择合适的工具。'''
    tools {
        include 'http_get'; include 'http_post'; include 'json_parse'; include 'json_query'
        include 'file_read'; include 'file_write'; include 'cmd_execute'

        tool('calculate') {
            description '计算数学表达式'
            returns 'string', '计算结果'
            timeout 10
            parameter { name 'expression'; type 'string'; required true; pattern '^[0-9+\\-*/().\\s]+$' }
            execute { params ->
                def expr = params.expression
                try { return "计算结果: ${expr} = ${new GroovyShell().evaluate(expr)}" }
                catch (Exception e) { return "计算失败: ${e.message}" }
            }
            onError { err -> "计算工具异常: ${err}" }
        }
    }
}

// ==========================================
// 2. 浏览器自动化控制 (Browser Use)
// ==========================================
// 运行示例: bin/agentdsl.sh run examples/tools-demo.agent.groovy --agent web-operator --chat "帮我打开百度网盘，搜索'AgentDSL'"
agent('web-operator') {
    description '能操控浏览器的智能体'
    model { provider 'gemini'; modelName 'gemini-2.5-flash'; temperature 0.0 }
    browser_use {
        sandbox false
        hitl_on 'click', 'fill', 'navigate' // 关键的操作会被拦截询问是否确认
    }
    systemPrompt '''你是一个能够浏览网页并操作浏览器的全能助手。
请使用提供的原生浏览器操作工具完成用户的指令。'''
}

// ==========================================
// 3. 代码执行器 (Groovy, Python, Shell)
// ==========================================
// 运行示例: bin/agentdsl.sh run examples/tools-demo.agent.groovy --agent code-executor-demo --chat "写一段 python 代码来计算斐波那契数列的前10项并运行"
agent('code-executor-demo') {
    description '安全代码执行助手'
    model { provider 'gemini'; modelName 'gemini-3.1-pro-preview' }
    tools {
        include 'groovy_execute'
        include 'shell_script_run'
        include 'python_run'
        include 'cmd_execute'
        include 'file_write'
        include 'file_read'
    }
    systemPrompt '''你是一个全能的助手，特别擅长使用代码执行工具完成任务；
精通groovy、shell、python三种编程语言，能够根据任务需求选择合适的编程语言和工具完成任务。
当你需要执行任务需求时，会先思考哪种编程语言最适合，然后生成代码并执行。
三种执行工具：groovy_execute (适合轻量数据处理), shell_script_run (Shell/Bat), python_run (Python脚本)。'''
}

// ==========================================
// 4. 视觉与多模态分析 (Vision)
// ==========================================
// 运行示例: export DASHSCOPE_API_KEY=your_key; bin/agentdsl.sh run examples/tools-demo.agent.groovy --agent vision_assistant --chat "分析一下这张图片: /path/to/img.jpg"
agent('vision_assistant') {
    description '一个具备视觉分析能力的助手，可以识别和描述图片内容。'
    model { provider 'qwen'; modelName 'qwen3.5-plus' }
    systemPrompt '''你是千问视觉助手。当用户提供图片时，请使用 vision_analyze 工具。
注意：端点和模型需要匹配提供商的视觉能力。'''
    tools { include 'vision_analyze' }
}

// ==========================================
// 5. 网络搜索 (Web Search)
// ==========================================
// 运行示例: export TAVILY_API_KEY=your_key; bin/agentdsl.sh run examples/tools-demo.agent.groovy --agent researcher --chat "What is the current price of Bitcoin?"
agent('researcher') {
    description 'A researcher agent that uses web search to answer questions.'
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    search {
        provider 'tavily'
        apiKey env('TAVILY_API_KEY')
    }
    systemPrompt "You are a helpful research assistant. When asked about current events, ALWAYS use the 'web_search' tool. After searching, synthesize the information clearly and cite the sources."
    tools { include 'web_search' }
}

// ==========================================
// 6. 办公文档处理 (Word 读写)
// ==========================================
// 运行示例: bin/agentdsl.sh run examples/tools-demo.agent.groovy --agent word-demo --chat "请生成一份介绍 AgentDSL 的测试文档"
agent('word-demo') {
    description 'Word 文档读取与生成测试'
    model { provider 'gemini'; modelName 'gemini-2.5-flash'; temperature 0.3 }
    systemPrompt '''你是一个办公自动化助手。主要任务是测试对 Word(.docx) 的写入和读取。
执行步骤：
1. 收到请求后，使用 `word_write` 生成一份排版精美的 Word 文档。
2. 将文件保存到 `/tmp/agentdsl_word_test.docx`。
3. 写入成功后，调用 `word_read` 读取你生成的这个文件，并将内容反馈给用户，证明功能正常。'''
    tools { include 'word_write'; include 'word_read' }
}

// ==========================================
// 7. 外部数据源与报表生成 (API 抓取 + 工作流)
// ==========================================
// 运行示例: bin/agentdsl.sh run examples/tools-demo.agent.groovy --workflow daily-reporting --input "分析用户列表数据"
agent('data-analyst') {
    description '企业数据分析专家'
    model { provider 'ollama'; modelName 'qwen2.5' }
    systemPrompt '''你是一位资深的商业数据分析师。你的任务是：
1. 理解用户的分析诉求
2. 调用工具获取真实的数据源
3. 深度分析数据特征，提炼出 3-5 个核心洞察
4. 最终汇总输出为 Markdown 格式的专业分析报告。'''
    tools { include 'http_get' }
}

workflow('daily-reporting') {
    description '自动化数据分析流水线'
    steps {
        step('analyze') {
            agent 'data-analyst'
            input { topic -> "请求分析任务：${topic}\n数据源请调用 http_get 获取这接口的数据：https://jsonplaceholder.typicode.com/users" }
        }
    }
}

// ==========================================
// 8. 基础 Docker 测试 Agent
// ==========================================
// 运行示例: bin/agentdsl.sh run examples/tools-demo.agent.groovy --agent test-docker --chat "Hello"
agent('test-docker') {
    model { provider 'ollama'; modelName 'qwen3:4b' }
    systemPrompt 'You are a test agent.'
}
