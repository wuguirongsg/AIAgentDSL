/**
 * 混合编排流水线 — Workflow 直接执行特性示例
 *
 * 本示例演示 AgentDSL 新增的三种直接执行模式（绕过 LLM，零 Token 消耗）：
 *
 *   execute { ctx -> ... }   — 纯代码执行节点：直接运行 Groovy 闭包
 *   tool "工具名"             — 工具节点：直接调用已注册工具
 *   skill "技能名"            — 技能节点：直接调用已注册 Logic Skill
 *
 * 以及传统的认知节点（保持原有能力）：
 *   agent "Agent名"          — 认知节点：调用大模型推理
 *
 * 使用方式：
 *
 *   # 工作流 1：纯代码 + 工具节点（无需 LLM，适合测试本地逻辑）
 *   agentdsl run examples/workflow-direct-execution.agent.groovy \
 *       --workflow order-processing-pipeline --input "ORD-20260310-001"
 *
 *   # 工作流 2：混合编排（execute 准备数据 → agent 分析 → execute 后处理）
 *   agentdsl run examples/workflow-direct-execution.agent.groovy \
 *       --workflow smart-report-pipeline --input "用户增长数据:新增1200人,留存率78%"
 *
 *   # 工作流 3：条件分支 + execute 节点（纯确定性路由，无 LLM）
 *   agentdsl run examples/workflow-direct-execution.agent.groovy \
 *       --workflow ticket-routing-pipeline --input "P0 级别故障：支付服务不可用"
 */

// ─────────────────────────────────────────────────────────────────────────────
// 全局工具定义（可被多个 Workflow 步骤直接调用）
// ─────────────────────────────────────────────────────────────────────────────

tool('format_timestamp') {
    description '将当前时间格式化为可读字符串'
    parameter {
        name 'pattern'
        type 'string'
        description '日期格式，如 yyyy-MM-dd HH:mm:ss'
        required false
    }
    execute { params ->
        def pattern = params?.pattern ?: 'yyyy-MM-dd HH:mm:ss'
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern(pattern))
    }
}

tool('json_build') {
    description '将键值对列表组装为 JSON 字符串'
    parameter {
        name 'data'
        type 'string'
        description '格式为 key1=value1,key2=value2 的字符串'
        required true
    }
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
    description '根据关键词判断工单优先级（P0/P1/P2），返回优先级标签'
    parameter {
        name 'text'
        type 'string'
        description '工单文本'
        required true
    }
    execute { params ->
        def text = params.text.toLowerCase()
        if (text.contains('p0') || text.contains('故障') || text.contains('不可用') || text.contains('崩溃')) {
            return 'P0'
        } else if (text.contains('p1') || text.contains('严重') || text.contains('异常')) {
            return 'P1'
        } else {
            return 'P2'
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 全局 Logic Skill 定义
// ─────────────────────────────────────────────────────────────────────────────

skill('report_formatter') {
    type 'logic'
    description '将分析结果格式化为标准报告文本'
    parameter {
        name 'title'
        type 'string'
        description '报告标题'
        required true
    }
    parameter {
        name 'content'
        type 'string'
        description '报告正文'
        required true
    }
    parameter {
        name 'timestamp'
        type 'string'
        description '生成时间'
        required false
    }
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

// ─────────────────────────────────────────────────────────────────────────────
// 认知 Agent 定义（仅用于需要推理的步骤）
// ─────────────────────────────────────────────────────────────────────────────

agent('data-analyst') {
    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.3
    }
    systemPrompt '''你是一位数据分析师。
用户提供原始业务数据，请给出简明扼要的趋势分析和建议，3-5 句话，中文回答。'''
}

agent('incident-handler') {
    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.1
    }
    systemPrompt '''你是一位资深 SRE（现场可靠性工程师）。
收到 P0 级别故障工单后，立即给出：
1. 可能原因（2-3 条）
2. 立即处置建议（2-3 条）
格式简洁，使用中文。'''
}

// ─────────────────────────────────────────────────────────────────────────────
// 工作流 1：订单处理流水线（纯确定性，全程零 LLM 调用）
//
// 演示要点：
//   - execute 步骤直接操作数据，无需 LLM
//   - tool 步骤直接调用已注册工具
//   - skill 步骤直接调用 Logic Skill 格式化输出
//   - execute 步骤内通过 ctx.toolCall() 调用工具
// ─────────────────────────────────────────────────────────────────────────────

workflow('order-processing-pipeline') {
    description '订单处理流水线：纯代码 + 工具节点，零 LLM 成本'

    steps {
        // Step 1: execute — 解析订单号，提取信息（纯代码）
        step('parse-order') {
            execute { ctx ->
                def orderId = ctx.lastOutput?.toString() ?: 'UNKNOWN'
                def parts = orderId.split('-')
                def date = parts.length > 1 ? parts[1] : 'unknown'
                def seq = parts.length > 2 ? parts[2] : '000'
                return "订单日期=${date},序号=${seq},状态=待处理,金额=299.00"
            }
        }

        // Step 2: tool — 直接调用 json_build 工具组装 JSON
        step('build-order-json') {
            tool 'json_build'
            input { orderData -> [data: orderData] }
        }

        // Step 3: execute — 在代码里直接调用 format_timestamp 工具，写入处理时间
        step('enrich-with-timestamp') {
            execute { ctx ->
                def orderJson = ctx.lastOutput?.toString() ?: '{}'
                def ts = ctx.toolCall('format_timestamp', [pattern: 'yyyy/MM/dd HH:mm'])
                // 把时间戳注入到数据中
                def enriched = orderJson.replace('}', ","+"\"processedAt\":\"${ts}\"}")
                return enriched
            }
        }

        // Step 4: skill — 直接调用 Logic Skill 格式化为最终报告
        step('format-report') {
            skill 'report_formatter'
            input { enrichedJson ->
                [
                    title  : '订单处理结果',
                    content: "已处理订单数据：\n${enrichedJson}",
                ]
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 工作流 2：智能数据报告流水线（混合编排）
//
// 演示要点：
//   - execute 步骤负责数据预处理（确定性，0 成本）
//   - agent 步骤负责推理分析（有 LLM，有成本，只用一次）
//   - execute 步骤负责后处理和格式化（确定性，0 成本）
//   - skill 步骤用确定性方式输出报告
// ─────────────────────────────────────────────────────────────────────────────

workflow('smart-report-pipeline') {
    description '智能报告流水线：execute 预处理 → agent 分析 → execute 后处理（混合编排）'

    steps {
        // Step 1: execute — 数据清洗与规范化（纯代码，0 Token）
        step('normalize-data') {
            execute { ctx ->
                def raw = ctx.lastOutput?.toString() ?: ''
                // 把冒号和逗号分隔的数据规范化为 key=value 格式
                def normalized = raw
                        .replace('：', ':')
                        .replace('，', ',')
                        .replaceAll(/([^:,]+):([^,]+)/) { all, k, v ->
                            "${k.trim()}=${v.trim()}"
                        }
                return "业务指标 | ${normalized} | 分析时间=${java.time.LocalDate.now()}"
            }
        }

        // Step 2: agent — 大模型推理（只有这一步消耗 Token）
        step('ai-analysis') {
            agent 'data-analyst'
            input { normalizedData -> "请分析以下业务数据并给出洞察：\n${normalizedData}" }
        }

        // Step 3: execute — 后处理：在分析结果前后加上标记（纯代码，0 Token）
        step('add-metadata') {
            execute { ctx ->
                def analysis = ctx.lastOutput?.toString() ?: ''
                def dataSource = ctx.getStepResult('normalize-data')?.toString() ?: ''
                return "【数据来源】${dataSource}\n\n【AI 分析】\n${analysis}"
            }
        }

        // Step 4: skill — 格式化为标准报告（直接调用 Logic Skill，0 Token）
        step('final-report') {
            skill 'report_formatter'
            input { content ->
                [
                    title  : '业务数据智能分析报告',
                    content: content,
                ]
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 工作流 3：智能工单路由（条件分支 + 混合编排）
//
// 演示要点：
//   - execute 步骤预分类（纯代码，确定性）
//   - condition + tool 节点：工具直接判断优先级，无需 LLM 做路由决策
//   - P0 分支：agent 处置（需要专业推理）→ skill 格式化报告
//   - P1/P2 分支：execute 快速处置（纯代码，0 Token）
// ─────────────────────────────────────────────────────────────────────────────

workflow('ticket-routing-pipeline') {
    description '工单智能路由：工具分类 → 条件分支 → P0 走 Agent，P1/P2 直接代码处置'

    steps {
        // Step 1: tool — 直接调用分类工具（跳过 LLM，100% 确定性分类）
        step('classify') {
            tool 'classify_priority'
            input { ticketText -> [text: ticketText] }
        }

        // Step 2: condition — 根据优先级路由（check 闭包里直接取工具输出）
        condition {
            check { result -> result.toString().trim() }

            // P0：最高优先级，需要 Agent 做专业 SRE 处置
            on('P0') {
                step('p0-ai-response') {
                    agent 'incident-handler'
                    input { priority ->
                        def ticket = "工单：${priority}"
                        "P0 故障工单，请立即给出处置方案：\n${ticket}"
                    }
                }
                step('p0-report') {
                    skill 'report_formatter'
                    input { response ->
                        [
                            title  : '🚨 P0 故障处置方案',
                            content: response,
                        ]
                    }
                }
            }

            // P1：次优先级，execute 快速预处置（0 Token）
            on('P1') {
                step('p1-quick-handle') {
                    execute { ctx ->
                        def ticket = ctx.lastOutput?.toString() ?: ''
                        def ts = ctx.toolCall('format_timestamp', [:])
                        return "【P1 工单预处置】\n" +
                               "• 已自动创建跟进任务\n" +
                               "• 已通知值班工程师\n" +
                               "• 预计响应时间：30 分钟内\n" +
                               "• 记录时间：${ts}"
                    }
                }
            }

            // P2：普通工单，execute 标准处置（0 Token）
            on('P2') {
                step('p2-standard-handle') {
                    execute { ctx ->
                        return "【P2 工单已入队】\n" +
                               "工单已进入标准处理队列，预计响应时间：4 小时内。"
                    }
                }
            }
        }
    }
}
