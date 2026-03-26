/**
 * 工作流 (Workflow) 示例合集
 * 演示了 AgentDSL 的各种流水线编排能力：
 *
 * 1. 基础工作流能力：
 *    - 顺序步骤 (step) 与数据传递 (input/output)
 *    - 并行步骤 (parallel)
 *    - 条件路由 (condition + on)
 *    - 循环迭代 (loop + until)
 *
 * 2. 混合编排与直接执行 (零 Token 消耗)：
 *    - execute { ctx -> ... }   — 纯代码执行节点：直接运行 Groovy 闭包
 *    - tool "工具名"             — 工具节点：直接调用已注册工具
 *    - skill "技能名"            — 技能节点：直接调用已注册 Logic Skill
 *    - agent "Agent名"          — 认知节点：调用大模型推理
 */


// =========================================================================
// 第一部分：翻译审核与多维度分析流水线（展示循环、并行、条件路由）
// =========================================================================

// ----- Agent 定义 -----
agent('translator') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '你是一个专业的中英文翻译员。用户给你中文，你翻译成英文。只输出翻译结果，不要解释。'
    memory { type 'message_window'; maxMessages 10 }
}

agent('reviewer') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '''你是翻译质量审核员。
审核翻译质量。
- 如果翻译合格，只返回 "pass"（不含引号）
- 如果不合格，返回一句简短的修改建议'''
}

agent('sentiment-analyzer') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '分析文本的情感倾向，只返回 positive、negative 或 neutral 三个词之一。'
}

agent('keyword-extractor') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '提取文本中最重要的 3-5 个关键词，用逗号分隔，不要其他内容。'
}

agent('report-generator') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '根据提供的分析结果，用两三句话生成一份简洁的总结报告。'
}

agent('premium-formatter') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '将内容润色为精致、正式的书面语格式输出。'
}

agent('standard-formatter') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash' }
    systemPrompt '将内容整理为清晰、简洁的标准格式输出。'
}


// ----- 工作流 1: 翻译审核流水线 (演示 Loop 迭代) -----
// 运行示例: bin/agentdsl.sh run examples/workflow-demo.agent.groovy --workflow translate-pipeline --input "你好，世界"
workflow('translate-pipeline') {
    description '翻译并审核的完整流水线'
    steps {
        step('translate') {
            agent 'translator'
            input { text -> "请翻译以下文本：\n${text}" }
        }
        loop(maxIterations: 3) {
            step('review') {
                agent 'reviewer'
                input { translated -> "请审核以下翻译：\n${translated}" }
            }
            until { result -> result.toString().trim().equalsIgnoreCase('pass') }
            step('revise') {
                agent 'translator'
                input { feedback -> "请根据以下反馈修改翻译：\n${feedback}" }
            }
        }
    }
}

// ----- 工作流 2: 多维度分析 (演示 Parallel 并行) -----
// 运行示例: bin/agentdsl.sh run examples/workflow-demo.agent.groovy --workflow multi-analysis --input "今天天气真好，心情非常舒畅！"
workflow('multi-analysis') {
    description '并行多维度分析后生成报告'
    steps {
        parallel {
            step('sentiment') { agent 'sentiment-analyzer' }
            step('keywords') { agent 'keyword-extractor' }
        }
        step('report') {
            agent 'report-generator'
            input { lastResult -> "分析结果：${lastResult}" }
        }
    }
}

// ----- 工作流 3: 条件路由 (演示 Condition 判断) -----
// 运行示例: bin/agentdsl.sh run examples/workflow-demo.agent.groovy --workflow quality-aware-format --input "一篇随笔文章内容..."
workflow('quality-aware-format') {
    description '根据质量评估结果选择不同的格式化策略'
    steps {
        step('assess') {
            agent 'reviewer'
            input { text -> "评估以下文本质量并打分，返回 premium（高质量）或 standard（标准）：\n${text}" }
        }
        condition {
            check { result -> result.toString().trim().toLowerCase().contains('premium') ? 'premium' : 'standard' }
            on('premium') { step('premium-format') { agent 'premium-formatter' } }
            on('standard') { step('standard-format') { agent 'standard-formatter' } }
        }
    }
}


// =========================================================================
// 第二部分：混合编排与直接执行 (演示直接调用 Tool/Skill/Code)
// =========================================================================

// ----- 工具 & Skill 定义 -----
tool('format_timestamp') {
    description '将当前时间格式化为可读字符串'
    parameter { name 'pattern'; type 'string'; required false }
    execute { params ->
        def pattern = params?.pattern ?: 'yyyy-MM-dd HH:mm:ss'
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(pattern))
    }
}

tool('json_build') {
    description '将键值对列表组装为 JSON 字符串'
    parameter { name 'data'; type 'string'; required true }
    execute { params ->
        def pairs = params.data.split(',')
        def map = [:]
        pairs.each { pair ->
            def kv = pair.split('=', 2)
            if (kv.length == 2) map[kv[0].trim()] = kv[1].trim()
        }
        return groovy.json.JsonOutput.toJson(map)
    }
}

tool('classify_priority') {
    description '根据关键词判断工单优先级'
    parameter { name 'text'; type 'string'; required true }
    execute { params ->
        def text = params.text.toLowerCase()
        if (text.contains('p0') || text.contains('故障') || text.contains('不可用') || text.contains('崩溃')) return 'P0'
        else if (text.contains('p1') || text.contains('严重') || text.contains('异常')) return 'P1'
        else return 'P2'
    }
}

skill('report_formatter') {
    type 'logic'
    description '将分析结果格式化为标准报告文本'
    parameter { name 'title'; type 'string'; required true }
    parameter { name 'content'; type 'string'; required true }
    parameter { name 'timestamp'; type 'string'; required false }
    execute { params ->
        def ts = params.timestamp ?: java.time.LocalDateTime.now().toString()
        return """
╔══════════════════════════════════════╗
  ${params.title}
╚══════════════════════════════════════╝

${params.content}

──────────────────────────────────────
生成时间：${ts}
""".stripIndent().trim()
    }
}

// ----- 认知节点 Agent -----
agent('data-analyst') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash'; temperature 0.3 }
    systemPrompt '你是一位数据分析师。用户提供业务数据，请给出简明趋势分析建议，3-5 句话，中文回答。'
}

agent('incident-handler') {
    model { provider 'gemini'; modelName 'gemini-2.5-flash'; temperature 0.1 }
    systemPrompt '''你是一位资深 SRE。收到 P0 级故障单后给出：1. 可能原因(2-3条) 2. 处置建议(2-3条)。格式简洁中文。'''
}

// ----- 工作流 4: 订单处理流水线（纯确定性，零 LLM） -----
// 运行示例: bin/agentdsl.sh run examples/workflow-demo.agent.groovy --workflow order-processing-pipeline --input "ORD-20260310-001"
workflow('order-processing-pipeline') {
    description '订单处理：纯代码 + 工具节点，零 LLM 成本'
    steps {
        step('parse-order') {
            execute { ctx ->
                def orderId = ctx.lastOutput?.toString() ?: 'UNKNOWN'
                def parts = orderId.split('-')
                def date = parts.length > 1 ? parts[1] : 'unknown'
                def seq = parts.length > 2 ? parts[2] : '000'
                return "订单日期=${date},序号=${seq},状态=待处理,金额=299.00"
            }
        }
        step('build-order-json') {
            tool 'json_build'
            input { orderData -> [data: orderData] }
        }
        step('enrich-with-timestamp') {
            execute { ctx ->
                def orderJson = ctx.lastOutput?.toString() ?: '{}'
                def ts = ctx.toolCall('format_timestamp', [pattern: 'yyyy/MM/dd HH:mm'])
                return orderJson.replace('}', ","+"\"processedAt\":\"${ts}\"}")
            }
        }
        step('format-report') {
            skill 'report_formatter'
            input { enrichedJson -> [ title: '订单处理结果', content: "已处理订单数据：\n${enrichedJson}" ] }
        }
    }
}

// ----- 工作流 5: 智能报告流水线（混合编排） -----
// 运行示例: bin/agentdsl.sh run examples/workflow-demo.agent.groovy --workflow smart-report-pipeline --input "用户增长数据:新增1200人,留存率78%"
workflow('smart-report-pipeline') {
    description '智能报告：execute 预处理 → agent 分析 → execute 后处理'
    steps {
        step('normalize-data') {
            execute { ctx ->
                def raw = ctx.lastOutput?.toString() ?: ''
                def normalized = raw.replace('：', ':').replace('，', ',').replaceAll(/([^:,]+):([^,]+)/) { all, k, v -> "${k.trim()}=${v.trim()}" }
                return "业务指标 | ${normalized} | 分析时间=${java.time.LocalDate.now()}"
            }
        }
        step('ai-analysis') {
            agent 'data-analyst'
            input { normalizedData -> "请分析以下业务数据并给出洞察：\n${normalizedData}" }
        }
        step('add-metadata') {
            execute { ctx ->
                def analysis = ctx.lastOutput?.toString() ?: ''
                def dataSource = ctx.getStepResult('normalize-data')?.toString() ?: ''
                return "【数据来源】${dataSource}\n\n【AI 分析】\n${analysis}"
            }
        }
        step('final-report') {
            skill 'report_formatter'
            input { content -> [ title: '业务数据智能分析报告', content: content ] }
        }
    }
}

// ----- 工作流 6: 工单智能路由 (混合编排) -----
// 运行示例: bin/agentdsl.sh run examples/workflow-demo.agent.groovy --workflow ticket-routing-pipeline --input "P0 级别故障：支付服务不可用"
workflow('ticket-routing-pipeline') {
    description '智能路由：工具分类 → 条件分支 → P0(Agent处理) 或 P1/P2(纯代码处置)'
    steps {
        step('classify') {
            tool 'classify_priority'
            input { ticketText -> [text: ticketText] }
        }
        condition {
            check { result -> result.toString().trim() }
            on('P0') {
                step('p0-ai-response') {
                    agent 'incident-handler'
                    input { priority -> "P0 故障工单，请立即给出处置方案：\n工单：${priority}" }
                }
                step('p0-report') {
                    skill 'report_formatter'
                    input { response -> [ title: '🚨 P0 故障处置方案', content: response ] }
                }
            }
            on('P1') {
                step('p1-quick-handle') {
                    execute { ctx ->
                        def ts = ctx.toolCall('format_timestamp', [:])
                        return "【P1 工单预处置】\n• 已通报\n• 预计30分内响应\n时间：${ts}"
                    }
                }
            }
            on('P2') {
                step('p2-standard-handle') {
                    execute { ctx -> return "【P2工单】标准队列，4小时内响应。" }
                }
            }
        }
    }
}
