/*
 * 长期记忆示例（超图记忆 + Gemini Embedding）
 *
 * 快速体验：
 *   第1次运行（存入偏好）：
 *     ./shell/agentdsl.sh run examples/long-memory-hypergraph.agent.groovy --chat "我叫张三，我喜欢喝黑咖啡，不加糖。"
 *
 *   第2次运行（跨会话回忆）：
 *     ./shell/agentdsl.sh run examples/long-memory-hypergraph.agent.groovy --chat "我的咖啡偏好是什么？"
 *     ./shell/agentdsl.sh run examples/long-memory-hypergraph.agent.groovy --chat "你知道我是谁吗？"
 *
 * 记忆架构说明：
 *   STM（短期）→ 当前会话 InMemory，应用重启后消失
 *   LTM（长期）→ SQLite 持久化，跨会话保留
 *   Archive（冷库）→ 向量文件，支持语义检索
 *
 *   高重要度消息（姓名、偏好等）会立即双写到 LTM，防止应用重启丢失。
 *   每次对话前自动在 LTM 中语义检索相关记忆注入上下文（proactiveRecall）。
 */

agent("memory-research-assistant") {
    description "具备长期记忆、深度回忆和超图记忆结构的研究助手"

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    systemPrompt """
        你是一个具备长期记忆能力的研究助手。
        你需要记住用户的偏好、长期任务和关键结论。

        【记忆规则】
        - 用户告知姓名、偏好、习惯等个人信息时，明确确认已记住。
        - 用户询问"你知道我是谁""我叫什么""我喜欢什么"等身份/偏好类问题时，
          必须先调用 deep_recall 查询长期记忆，再作答。
        - 如果记忆中确实没有相关信息，直接说明，不要编造。
    """

    memory {
        type "hypergraph"

        stm {
            maxEdges 20      // 当前会话保留的最大消息数
            ttlHours 24      // 超过24小时未访问的 STM 记忆自动整合到 LTM
        }

        ltm {
            backend "sqlite"
            path "./data/research_memory.db"
            compressionModel "gemini-2.5-flash"  // 用 LLM 生成高质量摘要，保留关键事实
        }

        vector {
            store "file-local"
            embeddingModel "gemini-embedding-001"  // Gemini embedding 模型（免费 tier 可用）
            // 如需更好的中文效果且 API key 已开启，可改为 "text-embedding-004"
            path "./data/research_memory.archive.json"
        }

        decay {
            baseRate 0.10
            importanceBoost 5.0
            compressionThreshold 0.35
            archiveThreshold 0.10
        }

        consolidation {
            intervalHours 6
        }

        deepRecallThreshold 0.70       // 使用真实 embedding 时，0.70 是较合理的语义相似度阈值
        proactiveRecallEnabled true    // 每轮对话前自动从 LTM 检索相关记忆注入上下文
        proactiveRecallThreshold 0.30  // 主动注入阈值，比 deepRecallThreshold 更宽松
        immediateFlushThreshold 0.65   // 重要度达到此值的消息（姓名/偏好）立即写入 LTM
    }

    skills {
        include "deep_recall"
    }
}
