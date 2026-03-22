package com.agentdsl.memory.hypergraph.capability;

import com.agentdsl.memory.hypergraph.HypergraphChatMemory;

import java.util.List;
import java.util.Map;

public class DeepRecallCapability implements MemoryCapability {

    private final HypergraphChatMemory memory;

    public DeepRecallCapability(HypergraphChatMemory memory) {
        this.memory = memory;
    }

    @Override
    public String getName() {
        return "deep_recall";
    }

    @Override
    public String getDescription() {
        return "从长期记忆和向量冷库中回忆与查询最相关的历史上下文。";
    }

    @Override
    public List<Map<String, Object>> getParameters() {
        return List.of(Map.of(
                "name", "query",
                "type", "string",
                "description", "需要回忆的主题、事实或问题",
                "required", true));
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String query = args.get("query") != null ? args.get("query").toString() : "";
        return memory.recall(query).orElse("No relevant long-term memory found.");
    }
}
