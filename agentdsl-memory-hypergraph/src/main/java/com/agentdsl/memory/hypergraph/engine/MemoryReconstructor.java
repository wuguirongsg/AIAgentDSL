package com.agentdsl.memory.hypergraph.engine;

import java.util.List;

public interface MemoryReconstructor {

    String reconstruct(String query, String summary, List<String> fragments);
}
