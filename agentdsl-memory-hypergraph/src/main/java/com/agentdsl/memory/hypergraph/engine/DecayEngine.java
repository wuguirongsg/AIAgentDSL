package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.config.DecayConfig;
import com.agentdsl.memory.hypergraph.model.HyperEdge;

import java.time.Duration;
import java.time.Instant;

/**
 * 记忆权重衰减引擎，基于改进的艾宾浩斯遗忘曲线。
 *
 * <h3>衰减公式</h3>
 * <pre>
 *   weight(t) = max(0.01, currentWeight) × e^(-λ × Δt) + accessCount × α
 *
 *   其中：
 *     λ = baseDecayRate / (1 + importance × importanceBoost)
 *     Δt = 距上次访问的小时数
 *     α = accessBonus（访问频次奖励系数）
 * </pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>重要度越高，衰减率 λ 越小，记忆保留越久</li>
 *   <li>锚点记忆（systemPrompt 等）不衰减，权重恒为 1.0</li>
 *   <li>访问频次提供正向奖励，类似 ACT-R 的 base-level activation</li>
 * </ul>
 *
 * <h3>配置参数</h3>
 * <ul>
 *   <li>{@code baseDecayRate} — 基础衰减率（默认 0.1）</li>
 *   <li>{@code importanceBoost} — 重要度对衰减的压制系数（默认 5.0）</li>
 *   <li>{@code accessBonus} — 每次访问的奖励权重（默认 0.05）</li>
 *   <li>{@code compressionThreshold} — 低于此权重触发 STM → LTM 压缩（默认 0.3）</li>
 *   <li>{@code archiveThreshold} — 低于此权重触发 LTM → Archive 归档（默认 0.1）</li>
 * </ul>
 *
 * @see DecayConfig
 * @see HyperEdge
 */
public class DecayEngine {

    private final DecayConfig config;

    /**
     * 使用给定配置创建衰减引擎。
     *
     * @param config 衰减参数配置（来自 DSL decay {} 块）
     */
    public DecayEngine(DecayConfig config) {
        this.config = config;
    }

    /**
     * 计算超边在指定时刻的当前权重。
     * <p>锚点记忆权重恒为 1.0，不受衰减影响。</p>
     *
     * @param edge 超边
     * @param now  当前时间
     * @return 权重值 [0.0, 1.0]
     */
    public double computeWeight(HyperEdge edge, Instant now) {
        if (edge.anchor()) {
            return 1.0;
        }

        double hours = Math.max(0.0, Duration.between(edge.lastAccessedAt(), now).toMinutes() / 60.0);
        double lambda = config.baseDecayRate() / (1.0 + edge.importance() * config.importanceBoost());
        double baseWeight = Math.max(0.01, edge.weight()) * Math.exp(-lambda * hours);
        double accessBoost = edge.accessCount() * config.accessBonus();
        return Math.min(1.0, baseWeight + accessBoost);
    }

    /**
     * 判断超边是否应该从 STM 压缩到 LTM。
     * <p>当权重低于 compressionThreshold 且非锚点时触发。</p>
     *
     * @param edge 超边
     * @param now  当前时间
     * @return true 表示应压缩
     */
    public boolean shouldCompress(HyperEdge edge, Instant now) {
        return !edge.anchor() && computeWeight(edge, now) < config.compressionThreshold();
    }

    /**
     * 判断超边是否应该从 LTM 归档到冷库。
     * <p>当权重低于 archiveThreshold 且非锚点时触发。</p>
     *
     * @param edge 超边
     * @param now  当前时间
     * @return true 表示应归档
     */
    public boolean shouldArchive(HyperEdge edge, Instant now) {
        return !edge.anchor() && computeWeight(edge, now) < config.archiveThreshold();
    }
}
