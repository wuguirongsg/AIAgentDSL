package com.agentdsl.memory.hypergraph.engine;

import java.util.Locale;

public class HeuristicImportanceScorer implements ImportanceScorer {

    @Override
    public double score(String content) {
        if (content == null || content.isBlank()) {
            return 0.1;
        }

        String normalized = content.toLowerCase(Locale.ROOT);
        double score = 0.35;

        if (normalized.length() > 80) {
            score += 0.10;
        }
        if (containsAny(normalized, "错误", "失败", "异常", "bug", "incident", "critical")) {
            score += 0.25;
        }
        if (containsAny(normalized, "决定", "计划", "deadline", "交付", "上线", "发布")) {
            score += 0.20;
        }
        if (containsAny(normalized, "喜欢", "偏好", "讨厌", "习惯", "记住")) {
            score += 0.15;
        }
        if (containsAny(normalized, "!", "！", "紧急", "必须")) {
            score += 0.10;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
