// examples/mcp-modelscope.agent.groovy
agent('modelscope-assistant') {
    description 'ModelScope 接口助手，能够使用 SSE MCP 与 ModelScope Hub API 进行交互。'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.1
    }

    mcp {
        // SSE 类型的 MCP 远程服务器
        server('modelscope_hub_api') {
            transport 'sse'
            // 请将 下方的 URL 替换为您真实的 SSE 接口地址
            url 'https://mcp.api-inference.modelscope.net/<你的UUID>/sse'

            // 可以配置连接超时时间（秒）
            timeout 60
        }
    }

    systemPrompt '''你是一个专门辅助工程师调用和查询 ModelScope Hub 的助手。
请使用通过 SSE MCP 提供给你的工具来完成用户的相关请求，例如查询模型信息、获取下载链接等。
如果接口返回异常，请友好地提示用户检查配置的 UUID 是否正确以及网络是否通畅。'''
}

// 运行示例（记得先替换脚本中的 url 为真实地址）：
// bin/agentdsl.sh run examples/mcp-modelscope.agent.groovy --chat "帮我查一下我查一下有哪些可以查询地图的mcp服务"
