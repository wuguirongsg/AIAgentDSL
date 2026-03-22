package com.agentdsl.memory.hypergraph.embedding;

import dev.langchain4j.data.embedding.Embedding;

public interface TextEmbeddingGenerator {

    Embedding embed(String text);

    int dimension();

    String modelName();
}
