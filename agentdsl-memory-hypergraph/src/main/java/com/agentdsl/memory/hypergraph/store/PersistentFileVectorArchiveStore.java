package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.embedding.TextEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量本地持久化冷库。
 * 通过 InMemoryEmbeddingStore 的文件序列化能力持久化向量，同时用 sidecar 文件保存 archive id 到原文的映射。
 */
public class PersistentFileVectorArchiveStore implements VectorArchiveStore {

    private final Path storeFile;
    private final Path textIndexFile;
    private final TextEmbeddingGenerator embeddingGenerator;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final Map<String, String> archivedTexts;

    public PersistentFileVectorArchiveStore(String storePath, TextEmbeddingGenerator embeddingGenerator) {
        this(Path.of(storePath), embeddingGenerator);
    }

    public PersistentFileVectorArchiveStore(Path storeFile, TextEmbeddingGenerator embeddingGenerator) {
        this.storeFile = storeFile;
        this.textIndexFile = resolveTextIndexFile(storeFile);
        this.embeddingGenerator = embeddingGenerator;
        ensureParentDirectory(storeFile);
        this.embeddingStore = loadEmbeddingStore(storeFile);
        this.archivedTexts = new ConcurrentHashMap<>(loadArchivedTexts(textIndexFile));
    }

    @Override
    public synchronized List<String> archive(HyperEdge edge) {
        String text = edge.messageText() != null ? edge.messageText() : "";
        String archiveId = embeddingStore.add(embeddingGenerator.embed(text), TextSegment.from(text));
        archivedTexts.put(archiveId, text);
        persist();
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
    public synchronized void clear() {
        embeddingStore.removeAll();
        archivedTexts.clear();
        persist();
    }

    private void persist() {
        embeddingStore.serializeToFile(storeFile);
        try {
            Files.writeString(textIndexFile, serializeArchivedTexts(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入 archive 文本索引失败: " + textIndexFile, e);
        }
    }

    private String serializeArchivedTexts() {
        StringBuilder builder = new StringBuilder();
        archivedTexts.forEach((id, text) -> builder.append(id)
                .append('\t')
                .append(Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)))
                .append('\n'));
        return builder.toString();
    }

    private static Map<String, String> loadArchivedTexts(Path path) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try {
            Map<String, String> result = new LinkedHashMap<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", 2);
                if (parts.length != 2) {
                    continue;
                }
                result.put(parts[0], new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("读取 archive 文本索引失败: " + path, e);
        }
    }

    private static InMemoryEmbeddingStore<TextSegment> loadEmbeddingStore(Path path) {
        if (!Files.exists(path)) {
            return new InMemoryEmbeddingStore<>();
        }
        return InMemoryEmbeddingStore.fromFile(path);
    }

    private static Path resolveTextIndexFile(Path storeFile) {
        String fileName = storeFile.getFileName() != null ? storeFile.getFileName().toString() : "archive-store.json";
        return storeFile.resolveSibling(fileName + ".texts");
    }

    private static void ensureParentDirectory(Path file) {
        Path parent = file.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("创建 archive 持久化目录失败: " + parent, e);
        }
    }
}
