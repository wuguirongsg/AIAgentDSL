package com.agentdsl.memory.hypergraph.embedding;

import dev.langchain4j.data.embedding.Embedding;

import java.nio.charset.StandardCharsets;

public class PseudoTextEmbeddingGenerator implements TextEmbeddingGenerator {

    private final int dimension;
    private final String modelName;

    public PseudoTextEmbeddingGenerator(int dimension, String modelName) {
        this.dimension = dimension;
        this.modelName = modelName != null ? modelName : "pseudo";
    }

    @Override
    public Embedding embed(String text) {
        float[] vector = new float[dimension];
        byte[] bytes = (text != null ? text : "").getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            return Embedding.from(vector);
        }
        for (int i = 0; i < bytes.length; i++) {
            int index = i % dimension;
            vector[index] += (bytes[i] & 0xFF) / 255.0f;
        }
        return Embedding.from(vector);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
