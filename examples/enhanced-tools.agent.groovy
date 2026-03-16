// 增强工具示例 — 演示内置工具引用和增强工具定义
// 运行: agentdsl run examples/enhanced-tools.agent.groovy

agent('tools-demo') {
    description '内置工具演示 Agent'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    systemPrompt '''你是一个全能助手，可以：
1. 使用 http_get / http_post 发送 HTTP 请求
2. 使用 json_parse / json_query 处理 JSON 数据
3. 使用 file_read / file_write 读写文件（仅限 /tmp 目录）

请根据用户需求选择合适的工具。'''

    tools {
        // 引用内置工具
        include 'http_get'
        include 'http_post'
        include 'json_parse'
        include 'json_query'
        include 'file_read'
        include 'file_write'
        include 'cmd_execute'

        // 自定义工具：增强语法演示
        tool('calculate') {
            description '计算数学表达式'
            returns 'string', '计算结果'
            timeout 10

            parameter {
                name 'expression'
                type 'string'
                description "数学表达式，如 '2 + 3 * 4'"
                required true
                pattern '^[0-9+\\-*/().\\s]+$'
            }

            execute { params ->
                def expr = params.expression
                try {
                    def result = new GroovyShell().evaluate(expr)
                    return "计算结果: ${expr} = ${result}"
                } catch (Exception e) {
                    return "计算失败: ${e.message}"
                }
            }

            onError { err ->
                "计算工具异常: ${err}"
            }
        }
    }
}
