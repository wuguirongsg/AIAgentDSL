package com.agentdsl.memory.hypergraph.engine;

import java.util.List;

public class HeuristicMemoryReconstructor implements MemoryReconstructor {

    @Override
    public String reconstruct(String query, String summary, List<String> fragments) {
        StringBuilder builder = new StringBuilder();
        builder.append("与查询相关的长期记忆");
        if (query != null && !query.isBlank()) {
            builder.append("（").append(query).append("）");
        }
        builder.append("：");
        if (summary != null && !summary.isBlank()) {
            builder.append(summary);
        }
        if (!fragments.isEmpty()) {
            builder.append("\n细节片段：");
            for (String fragment : fragments) {
                builder.append("\n- ").append(fragment);
            }
        }
        return builder.toString();
    }
}
