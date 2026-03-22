package com.agentdsl.core.spec;

public class MemoryStmSpec {

    private Integer maxEdges;
    private Integer ttlHours;

    public Integer getMaxEdges() {
        return maxEdges;
    }

    public void setMaxEdges(Integer maxEdges) {
        this.maxEdges = maxEdges;
    }

    public Integer getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(Integer ttlHours) {
        this.ttlHours = ttlHours;
    }

    @Override
    public String toString() {
        return "MemoryStmSpec{" +
                "maxEdges=" + maxEdges +
                ", ttlHours=" + ttlHours +
                '}';
    }
}
