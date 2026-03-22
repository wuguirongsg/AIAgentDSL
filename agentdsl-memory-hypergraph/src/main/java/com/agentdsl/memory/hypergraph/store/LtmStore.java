package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.MemoryNode;

import java.util.List;

public interface LtmStore {

    void save(HyperEdge edge);

    List<HyperEdge> findAll(String memoryId);

    List<HyperEdge> semanticSearch(String memoryId, String query, int limit);

    default void saveNodes(String memoryId, List<MemoryNode> nodes) {
    }

    default List<HyperEdge> findRelatedEdges(String memoryId, HyperEdge edge, int limit) {
        return List.of();
    }

    default void replaceLinkedEdgeIds(String edgeId, List<String> linkedEdgeIds) {
    }

    void markArchived(String edgeId);

    void clear(String memoryId);
}
