// examples/database-report.agent.groovy
// 演示结合外部数据源查询和深度分析生成 Markdown 报表的复杂流程
// 该脚本已被修改为完全可运行版本，使用了内置的 HTTP 工具拉取真实的远端 JSON 数据

agent('data-analyst') {
    description '企业数据分析专家'

    model {
        provider 'ollama'
        // 此处需要修改为你本地拥有的模型，如 qwen2.5
        modelName 'qwen2.5'
    }

    systemPrompt '''你是一位资深的商业数据分析师。你的任务是：
1. 理解用户的分析诉求
2. 调用工具获取真实的数据源
3. 深度分析数据特征，提炼出 3-5 个核心洞察
4. 最终汇总输出为 Markdown 格式的专业分析报告，报告必须包含结论。
'''

    tools {
        // 使用 AgentDSL 引擎系统内置的 http_get 工具获取真实网络数据
        include 'http_get'
    }
}

workflow('daily-reporting') {
    description '自动化数据分析流水线'

    steps {
        step('analyze') {
            agent 'data-analyst'
            input { topic ->
                "请求分析任务：${topic}\n数据源请调用 http_get 工具获取此接口的数据：https://jsonplaceholder.typicode.com/users"
            }
        }
    }
}
