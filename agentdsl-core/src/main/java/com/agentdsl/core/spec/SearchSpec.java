package com.agentdsl.core.spec;

public class SearchSpec {
    private String provider;
    private String apiKey;

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

    @Override
    public String toString() {
        return "SearchSpec{" +
                "provider='" + provider + '\'' +
                ", apiKey='***'" +
                '}';
    }
}
