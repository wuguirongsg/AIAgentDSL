package com.agentdsl.memory.hypergraph.capability;

import com.agentdsl.memory.hypergraph.HypergraphChatMemory;

import java.util.List;
import java.util.Map;

public class ConsolidateMemoryCapability implements MemoryCapability {

    private final HypergraphChatMemory memory;

    public ConsolidateMemoryCapability(HypergraphChatMemory memory) {
        this.memory = memory;
    }

    @Override
    public String getName() {
        return "consolidate_memory";
    }

    @Override
    public String getDescription() {
        return "手动触发一次 STM 到 LTM 的整合压缩和归档。";
    }

    @Override
    public List<Map<String, Object>> getParameters() {
        return List.of();
    }

    @Override
    public Object execute(Map<String, Object> args) {
        memory.consolidate();
        return "Memory consolidation completed.";
    }
}
