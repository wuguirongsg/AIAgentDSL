package com.agentdsl.memory.hypergraph;

import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.capability.ConsolidateMemoryCapability;
import com.agentdsl.memory.hypergraph.capability.DeepRecallCapability;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;
import java.util.Optional;

/**
 * Hypergraph memory 的 LangChain4j 适配层。
 */
public class HypergraphChatMemory implements ChatMemory {

    private final HypergraphMemoryConfig config;
    private final HypergraphMemoryStore store;

    public HypergraphChatMemory(HypergraphMemoryConfig config) {
        this(config, new HypergraphMemoryStore(config));
    }

    public HypergraphChatMemory(HypergraphMemoryConfig config, HypergraphMemoryStore store) {
        this.config = config;
        this.store = store;
    }

    @Override
    public Object id() {
        return config.memoryId();
    }

    @Override
    public void add(ChatMessage message) {
        store.add(message);
    }

    @Override
    public void set(Iterable<ChatMessage> messages) {
        store.set(messages);
    }

    @Override
    public List<ChatMessage> messages() {
        return store.messages();
    }

    @Override
    public void clear() {
        store.clear();
    }

    public void consolidate() {
        store.consolidate();
    }

    public Optional<String> recall(String query) {
        return store.recall(query);
    }

    public List<Object> getCapabilities() {
        return List.of(
                new DeepRecallCapability(this),
                new ConsolidateMemoryCapability(this));
    }
}
