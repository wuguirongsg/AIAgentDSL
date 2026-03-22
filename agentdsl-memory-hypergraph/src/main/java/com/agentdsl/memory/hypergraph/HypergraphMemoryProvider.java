package com.agentdsl.memory.hypergraph;

import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.embedding.EmbeddingGeneratorFactory;
import com.agentdsl.memory.hypergraph.embedding.LangChain4jEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.embedding.TextEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.engine.ChatModelMemoryReconstructor;
import com.agentdsl.memory.hypergraph.engine.ChatModelSummaryCompressor;
import com.agentdsl.memory.hypergraph.engine.HeuristicMemoryReconstructor;
import com.agentdsl.memory.hypergraph.engine.MemoryReconstructor;
import com.agentdsl.memory.hypergraph.engine.SummaryCompressor;
import com.agentdsl.memory.hypergraph.engine.SummaryCompressorFactory;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.Map;

/**
 * 供宿主通过反射发现的 memory provider。
 * 不依赖任何 AgentDSL 内部模块。
 */
public class HypergraphMemoryProvider {

    private static final String INTERNAL_COMPRESSION_CHAT_MODEL = "__agentdsl.memory.compressionChatModel";
    private static final String INTERNAL_RECONSTRUCTION_CHAT_MODEL = "__agentdsl.memory.reconstructionChatModel";
    private static final String INTERNAL_EMBEDDING_MODEL = "__agentdsl.memory.embeddingModel";

    public String getType() {
        return "hypergraph";
    }

    public int getPriority() {
        return 100;
    }

    public ChatMemory create(Map<String, Object> config) {
        HypergraphMemoryConfig memoryConfig = HypergraphMemoryConfig.from(config);
        SummaryCompressor summaryCompressor = createSummaryCompressor(config, memoryConfig);
        MemoryReconstructor reconstructor = createMemoryReconstructor(config);
        TextEmbeddingGenerator embeddingGenerator = createEmbeddingGenerator(config, memoryConfig);
        return new HypergraphChatMemory(memoryConfig,
                new HypergraphMemoryStore(memoryConfig, summaryCompressor, reconstructor, embeddingGenerator));
    }

    private SummaryCompressor createSummaryCompressor(Map<String, Object> config, HypergraphMemoryConfig memoryConfig) {
        SummaryCompressor fallback = SummaryCompressorFactory.create(memoryConfig.ltm());
        Object value = config.get(INTERNAL_COMPRESSION_CHAT_MODEL);
        if (value instanceof ChatModel chatModel) {
            return new ChatModelSummaryCompressor(chatModel, fallback);
        }
        return fallback;
    }

    private MemoryReconstructor createMemoryReconstructor(Map<String, Object> config) {
        MemoryReconstructor fallback = new HeuristicMemoryReconstructor();
        Object value = config.get(INTERNAL_RECONSTRUCTION_CHAT_MODEL);
        if (value instanceof ChatModel chatModel) {
            return new ChatModelMemoryReconstructor(chatModel, fallback);
        }
        return fallback;
    }

    private TextEmbeddingGenerator createEmbeddingGenerator(Map<String, Object> config,
            HypergraphMemoryConfig memoryConfig) {
        Object value = config.get(INTERNAL_EMBEDDING_MODEL);
        if (value instanceof EmbeddingModel embeddingModel) {
            return new LangChain4jEmbeddingGenerator(embeddingModel);
        }
        return EmbeddingGeneratorFactory.create(memoryConfig.vector());
    }
}
