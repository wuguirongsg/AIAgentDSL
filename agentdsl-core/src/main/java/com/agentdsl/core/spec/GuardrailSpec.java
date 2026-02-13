package com.agentdsl.core.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全护栏配置规范。
 * 对应 DSL 中的 guardrails { ... } 块。
 */
public class GuardrailSpec {

    private Integer maxTokensPerRequest;
    private List<String> blockedTopics = new ArrayList<>();
    private Object inputValidator; // Groovy Closure
    private Object outputValidator; // Groovy Closure

    // --- Getters & Setters ---

    public Integer getMaxTokensPerRequest() {
        return maxTokensPerRequest;
    }

    public void setMaxTokensPerRequest(Integer maxTokensPerRequest) {
        this.maxTokensPerRequest = maxTokensPerRequest;
    }

    public List<String> getBlockedTopics() {
        return blockedTopics;
    }

    public void setBlockedTopics(List<String> blockedTopics) {
        this.blockedTopics = blockedTopics;
    }

    public Object getInputValidator() {
        return inputValidator;
    }

    public void setInputValidator(Object inputValidator) {
        this.inputValidator = inputValidator;
    }

    public Object getOutputValidator() {
        return outputValidator;
    }

    public void setOutputValidator(Object outputValidator) {
        this.outputValidator = outputValidator;
    }

    @Override
    public String toString() {
        return "GuardrailSpec{" +
                "maxTokensPerRequest=" + maxTokensPerRequest +
                ", blockedTopics=" + blockedTopics +
                '}';
    }
}
