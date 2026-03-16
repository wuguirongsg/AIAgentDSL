// ============================================================
// AgentDSL 自主 Agent 改造效果测试脚本
// 覆盖：ReAct 循环、停滞检测、消息压缩、失败分类、TASK_COMPLETE
// ============================================================

// ── 场景一：多步信息收集与分析（测试基本 ReAct + TASK_COMPLETE）
agent('research-agent') {
    description '多步研究分析 Agent，验证 ReAct 循环和正常完成路径'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.3      // 低温度保证稳定性
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
        execution_mode 'plan'   // plan 模式：验证用户确认流程
        max_steps 15
    }
}

// ── 场景二：文件处理流水线（测试多工具协作 + 消息压缩）
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
                     category: ['A','B','C'][i % 3], active: i % 4 != 0]
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
        execution_mode 'fast'   // fast 模式：验证无规划直接执行
        max_steps 12
    }
}

// ── 场景三：网络探索 + 错误恢复（测试失败分类 + 停滞检测）
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
        max_steps 20     // 给足步数，验证错误后能继续
    }
}

/* 
# 验证 plan 模式确认流程 + ReAct 循环 + TASK_COMPLETE
shell/agentdsl.sh run examples/autonomous-performance.agent.groovy \
  --agent research-agent \
  --autonomous "在 /tmp 目录下创建一个名为 tech-report.md 的报告文件。
报告内容要求：
1. 用 cmd_execute 执行 'java -version' 和 'uname -a' 获取当前系统信息
2. 用 web_search 搜索 'LangChain4j 2025 latest features' 了解框架动态
3. 用 web_search 搜索 'Java AI agent framework comparison 2025'
4. 将以上信息整合成一份 Markdown 格式的技术报告
5. 用 file_write 保存到 /tmp/tech-report.md
6. 最后用 file_read 读取文件确认写入成功"

shell/agentdsl.sh run examples/autonomous-performance.agent.groovy \
  --agent file-pipeline-agent \
  --autonomous "在 /tmp 目录生成一批测试数据，做统计分析，把结果整理成报告" -ic


shell/agentdsl.sh run examples/autonomous-performance.agent.groovy \
  --agent research-agent \
  --autonomous "全面分析当前 AI Agent 领域的技术格局，包括主流框架、设计模式、各语言生态的差异，最终生成一份完整的技术白皮书"


*/