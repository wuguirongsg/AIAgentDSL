package com.agentdsl.memory.hypergraph.engine;

import com.agentdsl.memory.hypergraph.config.LtmConfig;

/**
 * 本地摘要压缩器工厂（无 LLM 依赖）。
 *
 * <p>返回最优的"无 LLM"压缩器：</p>
 * <ul>
 *   <li>配置了 {@code compressionModel}：说明用户期望高质量压缩，返回抽取式压缩器（句子粒度）
 *       作为 ChatModel 不可用时的本地兜底。
 *       {@link com.agentdsl.memory.hypergraph.HypergraphMemoryProvider} 在 ChatModel 可用时
 *       会将其包装为 {@code ChatModelSummaryCompressor}。</li>
 *   <li>未配置 {@code compressionModel}：返回启发式截断压缩器（最轻量）。</li>
 * </ul>
 *
 * <p>注意：此工厂不负责创建 {@code ChatModelSummaryCompressor}，
 * 因为它没有 ChatModel 实例。LLM 压缩器由
 * {@link com.agentdsl.memory.hypergraph.HypergraphMemoryProvider#createSummaryCompressor} 负责构建。</p>
 */
public final class SummaryCompressorFactory {

    private SummaryCompressorFactory() {
    }

    /**
     * 创建本地（无 LLM）摘要压缩器。
     * <ul>
     *   <li>有 compressionModel 配置 → {@link ExtractiveSummaryCompressor}（句子抽取，质量更好）</li>
     *   <li>无配置 → {@link HeuristicSummaryCompressor}（截断，最轻量）</li>
     * </ul>
     */
    public static SummaryCompressor create(LtmConfig ltmConfig) {
        if (ltmConfig != null && ltmConfig.compressionModel() != null && !ltmConfig.compressionModel().isBlank()) {
            return new ExtractiveSummaryCompressor();
        }
        return new HeuristicSummaryCompressor();
    }
}
