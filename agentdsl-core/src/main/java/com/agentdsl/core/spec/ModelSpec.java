package com.agentdsl.core.spec;

/**
 * LLM 模型配置规范。
 * 对应 DSL 中的 model { ... } 块。
 */
public class ModelSpec {

    private String provider;
    private String modelName;
    private String apiKey;
    private String baseUrl;
    private Double temperature = 0.7;
    private Double topP = 1.0;
    private Integer maxTokens = 2048;
    private Integer timeout = 60;

    // --- Getters & Setters ---

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return "ModelSpec{" +
                "provider='" + provider + '\'' +
                ", modelName='" + modelName + '\'' +
                ", temperature=" + temperature +
                '}';
    }
}
