package com.agentdsl.core.dsl

import com.agentdsl.core.spec.GuardrailSpec

/**
 * Guardrails 块的委托类。
 */
class GuardrailDelegate {

    private final GuardrailSpec spec

    GuardrailDelegate(GuardrailSpec spec) {
        this.spec = spec
    }

    void maxTokensPerRequest(Integer maxTokens) {
        spec.maxTokensPerRequest = maxTokens
    }

    void blockedTopics(String... topics) {
        spec.blockedTopics.addAll(topics)
    }

    void inputValidator(Closure<Boolean> validator) {
        spec.inputValidator = validator
    }

    void outputValidator(Closure<Boolean> validator) {
        spec.outputValidator = validator
    }
}
