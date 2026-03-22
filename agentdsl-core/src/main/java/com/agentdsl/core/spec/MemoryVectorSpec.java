package com.agentdsl.core.spec;

public class MemoryVectorSpec {

    private String store;
    private String embeddingModel;
    private String path;

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "MemoryVectorSpec{" +
                "store='" + store + '\'' +
                ", embeddingModel='" + embeddingModel + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}
