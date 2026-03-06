// examples/mcp-amap-maps.agent.groovy
agent('amap-navigator') {
    description '高德地图智能体，能够根据用户指令查询地理信息、路线等。'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.1
    }

    mcp {
        server('amap-maps') {
            command 'npx', '-y', '@amap/amap-maps-mcp-server'
        // 注意：请确保运行环境（或在运行脚本前）以环境变量方式注入了 AMAP_MAPS_API_KEY，例如：
        // export AMAP_MAPS_API_KEY="您的真实高德API_KEY"
        // bin/agentdsl.sh run examples/mcp-amap-maps.agent.groovy --chat "查一下从北京西站到天安门怎么走"
        }
    }

    systemPrompt '''你是一个专业的出行和地理信息助手。
请使用高德地图 (Amap Maps) 提供的 MCP 工具，辅助用户完成地点查询、路线规划等地理相关的请求。
如果请求失败，请友好地提示用户检查 AMAP_MAPS_API_KEY 是否设置或配额是否充足。'''
}

// 运行示例：
// export AMAP_MAPS_API_KEY="您的真实高德API_KEY"
// bin/agentdsl.sh run examples/mcp-amap-maps.agent.groovy --chat "帮我规划一下从北京西站到故宫博物院的地铁路线"
