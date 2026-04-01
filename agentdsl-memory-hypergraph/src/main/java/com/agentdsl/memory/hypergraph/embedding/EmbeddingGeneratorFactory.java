package com.agentdsl.memory.hypergraph.embedding;

import com.agentdsl.memory.hypergraph.config.VectorConfig;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

import java.util.Locale;

public final class EmbeddingGeneratorFactory {

    private EmbeddingGeneratorFactory() {
    }

    public static TextEmbeddingGenerator create(VectorConfig config) {
        String model = config.embeddingModel();
        if (model == null || model.isBlank()) {
            // 默认使用内置 AllMiniLmL6V2，比 PseudoTextEmbeddingGenerator 提供真实语义相似度
            return new LangChain4jEmbeddingGenerator(new AllMiniLmL6V2EmbeddingModel());
        }

        String normalized = model.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("all-minilm-l6-v2") || normalized.equals("allminilm-l6-v2")) {
            return new LangChain4jEmbeddingGenerator(new AllMiniLmL6V2EmbeddingModel());
        }

        // 其他模型名（如 text-embedding-004、bge-m3）由宿主通过 INTERNAL_EMBEDDING_MODEL 注入，
        // 此处作为兜底仍用 AllMiniLmL6V2，不再静默映射到错误模型
        return new LangChain4jEmbeddingGenerator(new AllMiniLmL6V2EmbeddingModel());
    }
}
