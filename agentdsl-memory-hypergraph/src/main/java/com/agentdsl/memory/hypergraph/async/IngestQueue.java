package com.agentdsl.memory.hypergraph.async;

import com.agentdsl.memory.hypergraph.model.HyperEdge;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 对话写入缓冲队列（v1.2 新增）。
 *
 * <p>对话路径调用 {@link #offer} 立即返回（< 1ms）。
 * 超边已在对话路径完成规则预评分并写入 STM，此队列供后台精确重评分使用。
 * 队列满时丢弃最旧的任务（LRU 策略），保证对话路径永不阻塞。</p>
 */
public class IngestQueue {

    /**
     * 待精确重评分的超边任务。
     *
     * @param edge      已写入 STM 的超边（含规则预评分）
     * @param rawText   原始文本（供 LLM 评分）
     * @param timestampMs 入队时间戳
     */
    public record IngestTask(
            HyperEdge edge,
            String rawText,
            long timestampMs) {
    }

    private final BlockingQueue<IngestTask> queue;

    public IngestQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * 非阻塞写入，队列满时丢弃最旧任务。
     *
     * @param edge    已写入 STM 的超边
     * @param rawText 原始文本（供后台精确评分）
     */
    public void offer(HyperEdge edge, String rawText) {
        IngestTask task = new IngestTask(edge, rawText, System.currentTimeMillis());
        if (!queue.offer(task)) {
            queue.poll();
            queue.offer(task);
        }
    }

    public BlockingQueue<IngestTask> getQueue() {
        return queue;
    }

    public int size() {
        return queue.size();
    }
}
