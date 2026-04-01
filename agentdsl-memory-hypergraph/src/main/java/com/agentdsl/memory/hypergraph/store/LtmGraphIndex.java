package com.agentdsl.memory.hypergraph.store;

import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.MetaHyperEdge;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.HashSet;
import java.util.Set;

/**
 * 基于 JGraphT 的 LTM 超图内存图索引。
 *
 * <p>维护两张加权图：</p>
 * <ul>
 *   <li>Level-1 图：摘要超边之间的关联网络（通过共享节点/标签建边）</li>
 *   <li>Level-2 图：元超边之间的主题关联网络</li>
 * </ul>
 *
 * <p>在服务启动时由 {@link SQLiteLtmStore} 从持久化数据重建。
 * 运行时由 ConsolidationEngine 和 AbstractionEngine 增量更新。</p>
 */
public class LtmGraphIndex {

    private final Graph<String, DefaultWeightedEdge> level1Graph =
            new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

    private final Graph<String, DefaultWeightedEdge> level2Graph =
            new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

    // ===== Level-1 超边图操作 =====

    /**
     * 将 LTM 超边加入 Level-1 图索引，并与 linkedEdgeIds 中已存在的节点建边。
     */
    public synchronized void addEdge(HyperEdge edge) {
        level1Graph.addVertex(edge.id());
        if (edge.linkedEdgeIds() != null) {
            for (String neighborId : edge.linkedEdgeIds()) {
                if (level1Graph.containsVertex(neighborId)) {
                    if (!level1Graph.containsEdge(edge.id(), neighborId)) {
                        DefaultWeightedEdge e = level1Graph.addEdge(edge.id(), neighborId);
                        level1Graph.setEdgeWeight(e, 1.0);
                    }
                }
            }
        }
    }

    /**
     * 从 Level-1 图中移除超边节点（归档时调用）。
     */
    public synchronized void removeEdge(String edgeId) {
        level1Graph.removeVertex(edgeId);
    }

    /**
     * 更新两条超边之间的关联权重。
     * 如果节点不存在则自动添加，如果边不存在则新建。
     */
    public synchronized void updateEdgeWeight(String fromId, String toId, double weight) {
        level1Graph.addVertex(fromId);
        level1Graph.addVertex(toId);
        if (!level1Graph.containsEdge(fromId, toId)) {
            DefaultWeightedEdge e = level1Graph.addEdge(fromId, toId);
            if (e != null) {
                level1Graph.setEdgeWeight(e, weight);
            }
        } else {
            level1Graph.setEdgeWeight(level1Graph.getEdge(fromId, toId), weight);
        }
    }

    /**
     * BFS N 跳邻居查找，返回指定节点在 Level-1 图中的 N 跳邻居 ID 集合（不含自身）。
     */
    public synchronized Set<String> findNeighbors(String edgeId, int hops) {
        Set<String> neighbors = new HashSet<>();
        if (!level1Graph.containsVertex(edgeId)) {
            return neighbors;
        }
        BreadthFirstIterator<String, DefaultWeightedEdge> bfs =
                new BreadthFirstIterator<>(level1Graph, edgeId);
        while (bfs.hasNext()) {
            String vertex = bfs.next();
            if (!vertex.equals(edgeId) && bfs.getDepth(vertex) <= hops) {
                neighbors.add(vertex);
            }
        }
        return neighbors;
    }

    /**
     * 判断两个节点之间是否存在路径（任意跳数）。
     */
    public synchronized boolean hasPath(String fromId, String toId) {
        if (!level1Graph.containsVertex(fromId) || !level1Graph.containsVertex(toId)) {
            return false;
        }
        BreadthFirstIterator<String, DefaultWeightedEdge> bfs =
                new BreadthFirstIterator<>(level1Graph, fromId);
        while (bfs.hasNext()) {
            if (bfs.next().equals(toId)) return true;
        }
        return false;
    }

    // ===== Level-2 元超边图操作 =====

    /**
     * 将元超边加入 Level-2 图索引。
     */
    public synchronized void addMetaEdge(MetaHyperEdge metaEdge) {
        level2Graph.addVertex(metaEdge.id());
    }

    /**
     * 在两条元超边之间建立关联（共享 contextTag 时调用）。
     */
    public synchronized void addMetaLink(String metaIdA, String metaIdB) {
        level2Graph.addVertex(metaIdA);
        level2Graph.addVertex(metaIdB);
        if (!level2Graph.containsEdge(metaIdA, metaIdB)) {
            level2Graph.addEdge(metaIdA, metaIdB);
        }
    }

    /**
     * 返回 Level-1 图中节点总数（用于测试和监控）。
     */
    public synchronized int level1Size() {
        return level1Graph.vertexSet().size();
    }

    /**
     * 返回 Level-2 图中节点总数。
     */
    public synchronized int level2Size() {
        return level2Graph.vertexSet().size();
    }
}
