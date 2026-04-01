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
        // 个人身份/偏好信息是最应该持久化的事实，给予高权重
        if (containsAny(normalized, "我叫", "我的名字", "名叫", "姓名", "名字是", "我是",
                "我住", "我的职业", "我工作", "我来自", "我今年")) {
            score += 0.30;
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
