package com.agentdsl.memory.hypergraph.engine;

public interface SummaryCompressor {

    String compress(String content, int maxLength);
}
