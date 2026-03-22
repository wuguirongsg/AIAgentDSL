package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;

import java.util.List;

public interface VectorArchiveStore {

    List<String> archive(HyperEdge edge);

    List<String> retrieve(List<String> archivePointers);

    void clear();
}
