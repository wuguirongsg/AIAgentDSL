package com.agentdsl.memory.hypergraph.engine;

public interface ImportanceScorer {

    double score(String content);
}
