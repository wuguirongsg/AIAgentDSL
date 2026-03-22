package com.agentdsl.memory.hypergraph.embedding;

import com.agentdsl.memory.hypergraph.config.VectorConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

import java.util.Locale;

public final class EmbeddingGeneratorFactory {

    private EmbeddingGeneratorFactory() {
    }

    public static TextEmbeddingGenerator create(VectorConfig config) {
        String model = config.embeddingModel();
        if (model == null || model.isBlank()) {
            return new PseudoTextEmbeddingGenerator(config.archiveEmbeddingDimension(), "pseudo");
        }

        String normalized = model.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("all-minilm-l6-v2")
                || normalized.equals("allminilm-l6-v2")
                || normalized.equals("bge-m3")) {
            EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            return new LangChain4jEmbeddingGenerator(embeddingModel);
        }

        return new PseudoTextEmbeddingGenerator(config.archiveEmbeddingDimension(), normalized);
    }
}
