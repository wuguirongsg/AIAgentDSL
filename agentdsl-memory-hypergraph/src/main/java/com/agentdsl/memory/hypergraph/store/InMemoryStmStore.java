package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * STM 使用 ConcurrentHashMap + 顺序队列。
 * 这样既满足 O(1) 读写，也保留消息顺序。
 */
public class InMemoryStmStore implements StmStore {

    private final ConcurrentHashMap<String, HyperEdge> edges = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> order = new ConcurrentLinkedDeque<>();

    @Override
    public void add(HyperEdge edge) {
        edges.put(edge.id(), edge);
        order.addLast(edge.id());
    }

    @Override
    public List<HyperEdge> snapshot() {
        return findAll();
    }

    @Override
    public List<HyperEdge> findAll() {
        List<HyperEdge> result = new ArrayList<>();
        for (String id : order) {
            HyperEdge edge = edges.get(id);
            if (edge != null) {
                result.add(edge);
            }
        }
        return result;
    }

    @Override
    public List<HyperEdge> evictOverflow(int maxSize) {
        List<HyperEdge> evicted = new ArrayList<>();
        while (edges.size() > maxSize) {
            String oldestId = order.pollFirst();
            if (oldestId == null) {
                break;
            }
            HyperEdge removed = edges.remove(oldestId);
            if (removed != null) {
                evicted.add(removed);
            }
        }
        return evicted;
    }

    @Override
    public List<HyperEdge> evictExpired(Instant now, Duration ttl) {
        List<HyperEdge> expired = new ArrayList<>();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return expired;
        }
        for (String id : new ArrayList<>(order)) {
            HyperEdge edge = edges.get(id);
            if (edge == null) {
                order.remove(id);
                continue;
            }
            if (Duration.between(edge.createdAt(), now).compareTo(ttl) >= 0) {
                order.remove(id);
                HyperEdge removed = edges.remove(id);
                if (removed != null) {
                    expired.add(removed);
                }
            }
        }
        return expired;
    }

    @Override
    public void remove(String edgeId) {
        edges.remove(edgeId);
        order.remove(edgeId);
    }

    @Override
    public void clear() {
        edges.clear();
        order.clear();
    }
}
