package com.agentdsl.core.spec;

/**
 * 内容检索器配置规范。
 * 对应 DSL 中的 contentRetriever { ... } 块。
 */
public class ContentRetrieverSpec {

    private String type = "embedding_store";
    private String embeddingModel;
    private Integer maxResults = 5;
    private Double minScore = 0.0;

    // --- Getters & Setters ---

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }

    @Override
    public String toString() {
        return "ContentRetrieverSpec{" +
                "type='" + type + '\'' +
                ", embeddingModel='" + embeddingModel + '\'' +
                ", maxResults=" + maxResults +
                '}';
    }
}
