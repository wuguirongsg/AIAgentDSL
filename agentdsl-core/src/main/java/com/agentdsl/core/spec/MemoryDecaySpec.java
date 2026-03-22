package com.agentdsl.core.spec;

public class MemoryDecaySpec {

    private Double baseRate;
    private Double importanceBoost;
    private Double compressionThreshold;
    private Double archiveThreshold;

    public Double getBaseRate() {
        return baseRate;
    }

    public void setBaseRate(Double baseRate) {
        this.baseRate = baseRate;
    }

    public Double getImportanceBoost() {
        return importanceBoost;
    }

    public void setImportanceBoost(Double importanceBoost) {
        this.importanceBoost = importanceBoost;
    }

    public Double getCompressionThreshold() {
        return compressionThreshold;
    }

    public void setCompressionThreshold(Double compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    public Double getArchiveThreshold() {
        return archiveThreshold;
    }

    public void setArchiveThreshold(Double archiveThreshold) {
        this.archiveThreshold = archiveThreshold;
    }

    @Override
    public String toString() {
        return "MemoryDecaySpec{" +
                "baseRate=" + baseRate +
                ", importanceBoost=" + importanceBoost +
                ", compressionThreshold=" + compressionThreshold +
                ", archiveThreshold=" + archiveThreshold +
                '}';
    }
}
