/**
 * skill-demo.agent.groovy
 *
 * Sprint A 验收示例：演示 Prompt Skill 和 Logic Skill 两种技能的完整用法。
 *
 * 运行方法（需配置 OLLAMA_BASE_URL 本地模型，或替换为 openai/gemini provider）：
 *   ./gradlew :agentdsl-cli:run --args="run examples/skill-demo.agent.groovy"
 */

// ─────────────────────────────────────────────────────────────────────────────
// 1. 定义 Prompt Skill（描述型）
//    LLM 驱动，无需编写业务逻辑代码。
//    Agent 调用此 Skill 时，instruction 将作为额外上下文注入到 LLM 系统提示中。
// ─────────────────────────────────────────────────────────────────────────────
skill('summarizeText') {
    type    'prompt'
    description '对给定文本生成简洁的摘要，不超过 3 句话。'
    instruction '''
        你是一位专业的文字摘要助手。
        请仔细阅读用户提供的内容，用 3 句以内的中文简洁准确地归纳核心要点。
        不要遗漏关键信息，不要添加个人观点。
    '''

    parameter {
        name        'text'
        type        'string'
        description '需要摘要的原始文本'
        required    true
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. 定义 Logic Skill（逻辑型）
//    Groovy Closure 驱动，适合确定性多步骤业务逻辑。
//    此 Skill 将字符串转换为 Markdown 格式的有序列表。
// ─────────────────────────────────────────────────────────────────────────────
skill('formatAsMarkdownList') {
    type    'logic'
    description '将逗号分隔的字符串列表格式化为 Markdown 有序列表。'

    parameter {
        name        'items'
        type        'string'
        description "逗号分隔的列表项，例如 '苹果,香蕉,橙子'"
        required    true
    }

    execute { params ->
        def itemList = params.items?.toString()?.split(',') ?: []
        def sb = new StringBuilder()
        itemList.eachWithIndex { item, idx ->
            sb.append("${idx + 1}. ${item.trim()}\n")
        }
        return sb.toString().trim()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. 定义 Agent，挂载两个 Skill
// ─────────────────────────────────────────────────────────────────────────────
agent('contentAssistant') {
    description '内容助手：具备文本摘要和列表格式化能力的 AI 助手。'

    model {
        provider  'ollama'
        modelName 'qwen2.5:3b'
    }

    systemPrompt '''
        你是一个内容助手，拥有以下专业技能：
        - summarizeText：对用户提供的文本生成精简摘要
        - formatAsMarkdownList：将逗号分隔内容格式化为 Markdown 列表

        请根据用户需求选择合适的技能来完成任务。
    '''

    skills {
        include 'summarizeText'
        include 'formatAsMarkdownList'
    }

    memory {
        type        'message_window'
        maxMessages 10
    }
}
