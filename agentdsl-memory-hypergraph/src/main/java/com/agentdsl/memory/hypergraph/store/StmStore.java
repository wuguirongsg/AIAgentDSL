package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface StmStore {

    void add(HyperEdge edge);

    List<HyperEdge> snapshot();

    List<HyperEdge> findAll();

    List<HyperEdge> evictOverflow(int maxSize);

    List<HyperEdge> evictExpired(Instant now, Duration ttl);

    void remove(String edgeId);

    void clear();
}
