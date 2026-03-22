package com.agentdsl.memory.hypergraph.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 过渡期的“模型感知”摘要器。
 * 当配置了 compressionModel 时，优先按句子和关键词做抽取式压缩，而不是简单截断。
 */
public class ExtractiveSummaryCompressor implements SummaryCompressor {

    @Override
    public String compress(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return "";
        }

        int safeLength = Math.max(24, maxLength);
        String[] parts = content.split("[。！？!?\\n]");
        List<String> selected = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (selected.isEmpty() || containsSignal(trimmed)) {
                selected.add(trimmed);
            }
            String joined = String.join("。", selected);
            if (joined.length() >= safeLength) {
                return joined.substring(0, safeLength) + "...";
            }
        }

        String result = String.join("。", selected);
        if (result.isBlank()) {
            result = content;
        }
        return result.length() <= safeLength ? result : result.substring(0, safeLength) + "...";
    }

    private boolean containsSignal(String text) {
        return text.contains("记住")
                || text.contains("偏好")
                || text.contains("决定")
                || text.contains("错误")
                || text.contains("发布")
                || text.contains("deadline");
    }
}
