package com.agentdsl.memory.hypergraph.engine;

public class HeuristicSummaryCompressor implements SummaryCompressor {

    @Override
    public String compress(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int safeLength = Math.max(24, maxLength);
        return content.length() <= safeLength ? content : content.substring(0, safeLength) + "...";
    }
}
