package com.agentdsl.memory.hypergraph.async;

import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.store.LtmGraphIndex;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 图索引异步更新线程（v1.2 新增）。
 *
 * <p>STM 写入完成后，图关联计算在此后台完成，不阻塞对话响应。
 * 接收来自 {@link ScoringBatchWorker} 的超边批次，更新 {@link LtmGraphIndex}。</p>
 */
public class GraphUpdateWorker {

    private static final Logger log = Logger.getLogger(GraphUpdateWorker.class.getName());

    private final LtmGraphIndex ltmGraphIndex;
    private final BlockingQueue<List<HyperEdge>> updateQueue = new ArrayBlockingQueue<>(100);
    private final ExecutorService executor;
    private volatile boolean running = false;

    public GraphUpdateWorker(LtmGraphIndex ltmGraphIndex) {
        this.ltmGraphIndex = ltmGraphIndex;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "hypergraph-graph-update");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        running = true;
        executor.submit(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    List<HyperEdge> batch = updateQueue.take();
                    batch.forEach(edge -> {
                        try {
                            ltmGraphIndex.addEdge(edge);
                        } catch (Exception e) {
                            log.log(Level.WARNING, "图索引更新失败 edgeId=" + edge.id(), e);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    /**
     * 提交一批超边到图索引更新队列（非阻塞）。
     */
    public void enqueue(List<HyperEdge> edges) {
        if (!updateQueue.offer(edges)) {
            log.fine("图索引更新队列已满，丢弃本批次 " + edges.size() + " 条");
        }
    }

    public void shutdown() {
        running = false;
        executor.shutdownNow();
    }
}
