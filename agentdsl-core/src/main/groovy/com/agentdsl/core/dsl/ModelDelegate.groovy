package com.agentdsl.core.dsl

import com.agentdsl.core.spec.ModelSpec

/**
 * Model 块的委托类。
 * 处理 model { ... } 内部的所有关键字。
 */
class ModelDelegate {

    private final ModelSpec spec

    ModelDelegate(ModelSpec spec) {
        this.spec = spec
    }

    void provider(String provider) {
        spec.provider = provider
    }

    void modelName(String modelName) {
        spec.modelName = modelName
    }

    void apiKey(String apiKey) {
        spec.apiKey = apiKey
    }

    void baseUrl(String baseUrl) {
        spec.baseUrl = baseUrl
    }

    void temperature(Double temperature) {
        spec.temperature = temperature
    }

    void topP(Double topP) {
        spec.topP = topP
    }

    void maxTokens(Integer maxTokens) {
        spec.maxTokens = maxTokens
    }

    void timeout(Integer timeout) {
        spec.timeout = timeout
    }

    void customSetting(String key, Object value) {
        spec.addCustomSetting(key, value)
    }
}
