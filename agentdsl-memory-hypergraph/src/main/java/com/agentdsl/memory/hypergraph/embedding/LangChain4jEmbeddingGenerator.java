package com.agentdsl.memory.hypergraph.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

public class LangChain4jEmbeddingGenerator implements TextEmbeddingGenerator {

    private final EmbeddingModel embeddingModel;

    public LangChain4jEmbeddingGenerator(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public Embedding embed(String text) {
        return embeddingModel.embed(text != null ? text : "").content();
    }

    @Override
    public int dimension() {
        return embeddingModel.dimension();
    }

    @Override
    public String modelName() {
        return embeddingModel.modelName();
    }
}
