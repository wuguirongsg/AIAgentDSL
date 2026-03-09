package com.agentdsl.core.spec;

public class SearchSpec {
    private String provider;
    private String apiKey;
    private Integer maxResults;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    @Override
    public String toString() {
        return "SearchSpec{" +
                "provider='" + provider + '\'' +
                ", apiKey='***'" +
                '}';
    }
}
