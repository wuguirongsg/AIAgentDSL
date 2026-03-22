/*
 * 长期记忆示例：
 * 1. 先运行：
 *    ./shell/agentdsl.sh run examples/long-memory-hypergraph.agent.groovy --chat "请记住我喜欢黑咖啡，不加糖。"
 * 2. 再次运行同一脚本并提问：
 *    ./shell/agentdsl.sh run examples/long-memory-hypergraph.agent.groovy --chat "我的咖啡偏好是什么？"
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
        当用户询问历史上下文、偏好或待办事项时，可以主动使用 deep_recall。
        如果记忆中没有足够信息，就明确说明没有找到，而不是编造。
    """

    memory {
        type "hypergraph"

        stm {
            maxEdges 1
            ttlHours 1
        }

        ltm {
            backend "sqlite"
            path "./data/research_memory.db"
            compressionModel "gemini-2.5-flash"
        }

        vector {
            store "file-local"
            //embeddingModel "bge-m3"
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

        deepRecallThreshold 0.85
    }

    skills {
        include "deep_recall"
    }
}
