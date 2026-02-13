package com.agentdsl.core.dsl

import com.agentdsl.core.spec.MemorySpec

/**
 * Memory 块的委托类。
 * 处理 memory { ... } 内部的所有关键字。
 */
class MemoryDelegate {

    private final MemorySpec spec

    MemoryDelegate(MemorySpec spec) {
        this.spec = spec
    }

    void type(String type) {
        spec.type = type
    }

    void maxMessages(Integer maxMessages) {
        spec.maxMessages = maxMessages
    }

    void maxTokens(Integer maxTokens) {
        spec.maxTokens = maxTokens
    }
}
