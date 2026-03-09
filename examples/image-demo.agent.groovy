agent('vision_assistant') {
    description '一个具备视觉分析能力的助手，可以识别和描述图片内容。'

    model {
        provider 'qwen'
        modelName 'qwen3.5-plus'
    }

    systemPrompt '''你是千问视觉助手。
    当用户提供图片时，请使用 image_recognize 工具。
    注意：请设置以下参数以使用千问原生接口：
    - endpoint: "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
    - model: "qwen-vl-plus" (或 qwen-vl-max)
    '''

    tools {
        include 'image_recognize'
    }
}

// 运行示例:
// 1. 设置环境变量 (支持 OpenAI 兼容接口，如 GPT-4o, Claude 3, 或国产大模型视觉端点)
// export OPENAI_API_KEY=your_api_key
// export DASHSCOPE_API_KEY=your_api_key

// 2. 运行脚本并提问
// shell/agentdsl.sh run examples/image-demo.agent.groovy --chat "分析一下这个图片里有什么: https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg"

// 或者分析本地图片:
// shell/agentdsl.sh run examples/image-demo.agent.groovy --chat "帮我识别一下这个本地文件的内容: /Users/wuguirong/Downloads/test.jpg"
