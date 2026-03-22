package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.embedding.TextEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冷存储只依赖 LangChain4j EmbeddingStore 抽象。
 * 默认实现使用 InMemoryEmbeddingStore，后续可在插件内部替换 Milvus/PGVector/ES 等实现。
 */
public class EmbeddingStoreVectorArchiveStore implements VectorArchiveStore {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final TextEmbeddingGenerator embeddingGenerator;
    private final Map<String, String> archivedTexts = new ConcurrentHashMap<>();

    public EmbeddingStoreVectorArchiveStore(TextEmbeddingGenerator embeddingGenerator) {
        this(new InMemoryEmbeddingStore<>(), embeddingGenerator);
    }

    public EmbeddingStoreVectorArchiveStore(EmbeddingStore<TextSegment> embeddingStore,
            TextEmbeddingGenerator embeddingGenerator) {
        this.embeddingStore = embeddingStore;
        this.embeddingGenerator = embeddingGenerator;
    }

    @Override
    public List<String> archive(HyperEdge edge) {
        String text = edge.messageText() != null ? edge.messageText() : "";
        String archiveId = embeddingStore.add(embeddingGenerator.embed(text), TextSegment.from(text));
        archivedTexts.put(archiveId, text);
        return List.of(archiveId);
    }

    @Override
    public List<String> retrieve(List<String> archivePointers) {
        return archivePointers.stream()
                .map(archivedTexts::get)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    @Override
    public void clear() {
        embeddingStore.removeAll();
        archivedTexts.clear();
    }
}
