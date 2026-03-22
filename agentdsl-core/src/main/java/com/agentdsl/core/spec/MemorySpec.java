package com.agentdsl.core.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 记忆配置规范。
 * 对应 DSL 中的 memory { ... } 块。
 */
public class MemorySpec {

    private String type = "message_window";
    private Integer maxMessages = 20;
    private Integer maxTokens;
    private MemoryStmSpec stm;
    private MemoryLtmSpec ltm;
    private MemoryVectorSpec vector;
    private MemoryDecaySpec decay;
    private MemoryConsolidationSpec consolidation;
    private Double deepRecallThreshold;
    private final Map<String, Object> options = new LinkedHashMap<>();

    // --- Getters & Setters ---

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(Integer maxMessages) {
        this.maxMessages = maxMessages;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public MemoryStmSpec getStm() {
        return stm;
    }

    public void setStm(MemoryStmSpec stm) {
        this.stm = stm;
    }

    public MemoryLtmSpec getLtm() {
        return ltm;
    }

    public void setLtm(MemoryLtmSpec ltm) {
        this.ltm = ltm;
    }

    public MemoryVectorSpec getVector() {
        return vector;
    }

    public void setVector(MemoryVectorSpec vector) {
        this.vector = vector;
    }

    public MemoryDecaySpec getDecay() {
        return decay;
    }

    public void setDecay(MemoryDecaySpec decay) {
        this.decay = decay;
    }

    public MemoryConsolidationSpec getConsolidation() {
        return consolidation;
    }

    public void setConsolidation(MemoryConsolidationSpec consolidation) {
        this.consolidation = consolidation;
    }

    public Double getDeepRecallThreshold() {
        return deepRecallThreshold;
    }

    public void setDeepRecallThreshold(Double deepRecallThreshold) {
        this.deepRecallThreshold = deepRecallThreshold;
    }

    public Map<String, Object> getOptions() {
        return Collections.unmodifiableMap(options);
    }

    public void putOption(String key, Object value) {
        options.put(key, value);
    }

    public Object getOption(String key) {
        return options.get(key);
    }

    @Override
    public String toString() {
        return "MemorySpec{" +
                "type='" + type + '\'' +
                ", maxMessages=" + maxMessages +
                ", maxTokens=" + maxTokens +
                ", stm=" + stm +
                ", ltm=" + ltm +
                ", vector=" + vector +
                ", decay=" + decay +
                ", consolidation=" + consolidation +
                ", deepRecallThreshold=" + deepRecallThreshold +
                ", options=" + options +
                '}';
    }
}
