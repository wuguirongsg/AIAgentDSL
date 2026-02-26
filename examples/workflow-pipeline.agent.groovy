/**
 * 翻译审核流水线 — AgentDSL v1.1 工作流示例
 * 使用本地 Ollama qwen3:4b 模型
 *
 * 演示：
 * - Agent + Workflow 混合定义
 * - 顺序步骤 (step) 与数据传递 (input/output)
 * - 并行步骤 (parallel)
 * - 条件路由 (condition + on)
 * - 循环迭代 (loop + until)
 */

// ===== 定义 Agent =====

agent('translator') {
    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }
    systemPrompt '你是一个专业的中英文翻译员。用户给你中文，你翻译成英文。只输出翻译结果，不要解释。'
}

agent('reviewer') {
    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }
    systemPrompt '''你是翻译质量审核员。
审核翻译质量。
- 如果翻译合格，只返回 "pass"（不含引号）
- 如果不合格，返回一句简短的修改建议'''
}

agent('sentiment-analyzer') {
    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }
    systemPrompt '分析文本的情感倾向，只返回 positive、negative 或 neutral 三个词之一。'
}

agent('keyword-extractor') {
    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }
    systemPrompt '提取文本中最重要的 3-5 个关键词，用逗号分隔，不要其他内容。'
}

agent('report-generator') {
    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }
    systemPrompt '根据提供的分析结果，用两三句话生成一份简洁的总结报告。'
}

agent('premium-formatter') {
    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }
    systemPrompt '将内容润色为精致、正式的书面语格式输出。'
}

agent('standard-formatter') {
    model {
        provider 'ollama'
        modelName 'qwen3:4b'
    }
    systemPrompt '将内容整理为清晰、简洁的标准格式输出。'
}

// ===== 工作流 1: 翻译审核流水线 =====

workflow('translate-pipeline') {
    description '翻译并审核的完整流水线'

    steps {
        // 步骤 1：翻译
        step('translate') {
            agent 'translator'
            input { text -> "请翻译以下文本：\n${text}" }
        }

        // 步骤 2：循环审核 — 最多 3 次，直到通过
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

// ===== 工作流 2: 多维度分析 =====

workflow('multi-analysis') {
    description '并行多维度分析后生成报告'

    steps {
        // 步骤 1：并行分析
        parallel {
            step('sentiment') {
                agent 'sentiment-analyzer'
            }
            step('keywords') {
                agent 'keyword-extractor'
            }
        }

        // 步骤 2：汇总报告
        step('report') {
            agent 'report-generator'
            input { lastResult -> "分析结果：${lastResult}" }
        }
    }
}

// ===== 工作流 3: 条件路由 =====

workflow('quality-aware-format') {
    description '根据质量评估结果选择不同的格式化策略'

    steps {
        step('assess') {
            agent 'reviewer'
            input { text -> "评估以下文本质量并打分，返回 premium（高质量）或 standard（标准）：\n${text}" }
        }

        condition {
            check { result -> result.toString().trim().toLowerCase().contains('premium') ? 'premium' : 'standard' }

            on('premium') {
                step('premium-format') {
                    agent 'premium-formatter'
                }
            }

            on('standard') {
                step('standard-format') {
                    agent 'standard-formatter'
                }
            }
        }
    }
}
