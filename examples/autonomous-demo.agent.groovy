/**
 * Autonomous (自主) Agent 示例合集
 * 演示了 AgentDSL 的各类自主运行模式：
 * - Plan 模式 (先生成执行计划，确认后执行)
 * - Fast 模式 (跳过规划直接执行)
 * - Smart Preset 模式 (四阶段 Pipeline: 解构、多候选策略、元认知监控等)
 * - 动态发现 MCP Server
 * - 性能与鲁棒性测试 (复杂流水线、错误恢复等)
 */

// ==========================================
// Plan 模式示例：先生成执行计划，用户确认后再执行
// ==========================================
// 运行示例:
// bin/agentdsl.sh run examples/autonomous-demo.agent.groovy --agent PlanAgent --autonomous "帮我搜索 AgentDSL 项目的信息并整理"
agent('PlanAgent') {
    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    autonomous {
        execution_mode 'plan'
        max_steps 10
    }

    tools {
        include 'http_get'
        include 'web_search'
        include 'json_parse'
        include 'json_query'
        include 'file_read'
        include 'file_write'
    }

    search {
        provider 'tavily'
        apiKey env('TAVILY_API_KEY') // Assumes you have TAVILY_API_KEY exported in your environment
    }

    systemPrompt '''你是一个自主任务助手，帮助用户完成复杂的多步骤任务。
你擅长：
- 从网络获取信息
- 整理和分析数据
- 将结果保存到文件

请根据用户的需求，自主规划并执行任务。'''
}

// ==========================================
// Fast 模式示例：跳过规划，直接开始执行
// ==========================================
// 运行示例:
// bin/agentdsl.sh run examples/autonomous-demo.agent.groovy --agent FastAgent --autonomous "用 HTTP 请求获取 https://httpbin.org/get 的内容"
agent('FastAgent') {
    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    autonomous {
        execution_mode 'fast'
        max_steps 5
    }

    tools {
        include 'http_get'
        include 'web_search'
        include 'json_parse'
    }

    systemPrompt '你是一个自主任务助手，帮助用户完成复杂的多步骤任务。'
}

// ==========================================
// 动态发现 MCP 示例 (auto-mcp-agent)
// ==========================================
// 运行示例:
// bin/agentdsl.sh run examples/autonomous-demo.agent.groovy --agent auto-mcp-agent --autonomous "请帮我查询杭州今天天气，并给出穿衣建议"
agent('auto-mcp-agent') {
    description '自主执行 Agent，遇到缺失工具时自动发现并挂载 MCP'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    autonomous {
        execution_mode 'fast'
        max_steps 6
    }

    // 开启动态发现 MCP（关键配置）
    auto_discover_mcp true
    mcp_registry 'mcp.so'

    systemPrompt '''
你是一个自主执行助手。

执行原则：
1. 优先直接完成用户目标。
2. 当你需要调用工具但当前工具集中不存在时，允许触发动态 MCP 发现机制。
3. 成功拿到工具结果后，再给出简洁中文结论。

对于“天气/地图/GitHub 查询”类任务，请优先尝试工具调用；若工具暂不可用，继续尝试并等待系统自动发现可用 MCP。
'''
}

// ==========================================
// Smart Preset 示例 (四阶段 Pipeline: 解构、ToT多候选策略、元认知监控)
// ==========================================
// 运行示例:
// export TAVILY_API_KEY=your api key
// bin/agentdsl.sh run examples/autonomous-demo.agent.groovy --agent SmartAgent --autonomous "去github上找一下OpenCode的代码，然后下载下来，然后分析一下代码..."
agent('SmartAgent') {
    model {
        provider 'gemini'
        modelName 'gemini-3.1-pro-preview'
        maxTokens 8192
    }

    autonomous {
        execution_mode 'plan'
        max_steps 20
        preset 'smart'
        max_token_budget 100000
        max_time_ms 600000
    }

    tools {
        include 'http_get'
        include 'json_parse'
        include 'file_read'
        include 'file_write'
        include 'web_search'
        include 'cmd_execute'
        include 'groovy_execute'
        include 'shell_script_run'
        include 'python_run'
    }

    search {
        provider 'tavily'
        apiKey env('TAVILY_API_KEY')
    }

    systemPrompt '''你是自主任务助手。遵循 System Prompt 中注入的成功标准与策略步骤；
遇到元认知干预提示时，按要求切换思路、压缩上下文或收敛结论。'''
}

// ==========================================
// 性能与鲁棒性验证：多步信息收集与分析 (research-agent)
// ==========================================
// 运行示例:
// bin/agentdsl.sh run examples/autonomous-demo.agent.groovy --agent research-agent --autonomous "在 /tmp 目录下创建一个名为 tech-report.md 的报告文件..."
agent('research-agent') {
    description '多步研究分析 Agent，验证 ReAct 循环和正常完成路径'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.3
        maxTokens 4096
    }

    systemPrompt '''你是一个严谨的技术研究助手。
执行步骤时必须逐步进行，每步骤完成后评估进展。
完成所有要求后必须输出 TASK_COMPLETE 信号。'''

    memory {
        type 'message_window'
        maxMessages 30
    }

    tools {
        include 'file_read'
        include 'file_write'
        include 'cmd_execute'
        include 'web_search'
        include 'http_get'
        include 'json_parse'
    }

    autonomous {
        execution_mode 'plan'
        max_steps 15
    }
}

// ==========================================
// 性能与鲁棒性验证：文件处理流水线 (file-pipeline-agent)
// ==========================================
// 运行示例:
// bin/agentdsl.sh run examples/autonomous-demo.agent.groovy --agent file-pipeline-agent --autonomous "在 /tmp 目录生成一批测试数据，做统计分析，把结果整理成报告" -ic
agent('file-pipeline-agent') {
    description '文件处理流水线 Agent，步骤多，验证消息压缩和长任务'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.2
        maxTokens 4096
    }

    systemPrompt '''你是一个文件处理专家。
按照用户要求逐步处理文件，每步完成后检查结果。
所有步骤完成后输出 TASK_COMPLETE: [处理结果摘要]'''

    tools {
        include 'file_read'
        include 'file_write'
        include 'cmd_execute'
        include 'json_parse'

        // 自定义工具：生成测试数据
        tool('generate_test_data') {
            description '生成指定数量的测试数据条目，写入 JSON 文件'
            parameter {
                name 'count'
                type 'integer'
                description '生成的数据条数'
                required true
                min 1
                max 100
            }
            parameter {
                name 'output_path'
                type 'string'
                description '输出文件路径'
                required true
            }
            execute { params ->
                def count = params.count as int
                def items = (1..count).collect { i ->
                    [id: i, name: "item_${i}", value: Math.random() * 100 as int,
                     category: ['A', 'B', 'C'][i % 3], active: i % 4 != 0]
                }
                def json = groovy.json.JsonOutput.prettyPrint(
                    groovy.json.JsonOutput.toJson([total: count, items: items])
                )
                new File(params.output_path).text = json
                return "已生成 ${count} 条测试数据，写入 ${params.output_path}"
            }
        }

        // 自定义工具：数据统计分析
        tool('analyze_data') {
            description '读取 JSON 数据文件并返回统计分析结果'
            parameter {
                name 'file_path'
                type 'string'
                description 'JSON 数据文件路径'
                required true
            }
            execute { params ->
                try {
                    def text = new File(params.file_path).text
                    def data = new groovy.json.JsonSlurper().parseText(text)
                    def items = data.items

                    def byCategory = items.groupBy { it.category }
                    def categoryStats = byCategory.collectEntries { cat, group ->
                        [cat, [count: group.size(),
                               avgValue: (group.sum { it.value } / group.size()).round(2),
                               activeCount: group.count { it.active }]]
                    }
                    def totalActive = items.count { it.active }

                    return groovy.json.JsonOutput.prettyPrint(
                        groovy.json.JsonOutput.toJson([
                            totalItems: items.size(),
                            activeItems: totalActive,
                            inactiveItems: items.size() - totalActive,
                            categoryBreakdown: categoryStats
                        ])
                    )
                } catch (Exception e) {
                    return "分析失败: ${e.message}"
                }
            }
        }
    }

    autonomous {
        execution_mode 'fast'
        max_steps 12
    }
}

// ==========================================
// 性能与鲁棒性验证：网络探索 + 错误恢复 (resilient-agent)
// ==========================================
// 运行示例:
// bin/agentdsl.sh run examples/autonomous-demo.agent.groovy --agent resilient-agent --autonomous "尝试从有概率失败的接口获取信息"
agent('resilient-agent') {
    description '韧性 Agent，故意包含会失败的路径，验证错误分类和恢复'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.4
        maxTokens 4096
    }

    systemPrompt '''你是一个有韧性的信息收集助手。
遇到错误时要分析原因并换一种方法继续，不要放弃。
收集完成后将结果保存到文件并输出 TASK_COMPLETE。'''

    tools {
        include 'http_get'
        include 'web_search'
        include 'file_write'
        include 'file_read'
        include 'json_parse'

        // 故意会失败的工具（测试失败分类）
        tool('unreliable_fetch') {
            description '获取指定 URL 的内容（有概率失败，用于测试错误恢复）'
            parameter {
                name 'url'
                type 'string'
                description '目标 URL'
                required true
            }
            execute { params ->
                // 模拟随机失败
                if (Math.random() < 0.6) {
                    throw new java.net.ConnectException(
                        "Connection refused: ${params.url}"
                    )
                }
                return "成功获取: ${params.url} 的内容（模拟数据）"
            }
            onError { err ->
                "获取失败: ${err}"
            }
        }
    }

    autonomous {
        execution_mode 'fast'
        max_steps 20
    }
}
