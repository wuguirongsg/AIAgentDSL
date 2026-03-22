package com.agentdsl.core.spec;

public class MemoryLtmSpec {

    private String backend;
    private String path;
    private String compressionModel;

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCompressionModel() {
        return compressionModel;
    }

    public void setCompressionModel(String compressionModel) {
        this.compressionModel = compressionModel;
    }

    @Override
    public String toString() {
        return "MemoryLtmSpec{" +
                "backend='" + backend + '\'' +
                ", path='" + path + '\'' +
                ", compressionModel='" + compressionModel + '\'' +
                '}';
    }
}
