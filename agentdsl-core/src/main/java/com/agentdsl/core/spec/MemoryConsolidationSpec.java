package com.agentdsl.core.spec;

public class MemoryConsolidationSpec {

    private Integer intervalHours;

    public Integer getIntervalHours() {
        return intervalHours;
    }

    public void setIntervalHours(Integer intervalHours) {
        this.intervalHours = intervalHours;
    }

    @Override
    public String toString() {
        return "MemoryConsolidationSpec{" +
                "intervalHours=" + intervalHours +
                '}';
    }
}
