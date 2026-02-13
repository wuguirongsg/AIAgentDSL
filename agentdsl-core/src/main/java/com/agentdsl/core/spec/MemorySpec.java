package com.agentdsl.core.spec;

/**
 * 记忆配置规范。
 * 对应 DSL 中的 memory { ... } 块。
 */
public class MemorySpec {

    private String type = "message_window";
    private Integer maxMessages = 20;
    private Integer maxTokens;

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

    @Override
    public String toString() {
        return "MemorySpec{" +
                "type='" + type + '\'' +
                ", maxMessages=" + maxMessages +
                '}';
    }
}
