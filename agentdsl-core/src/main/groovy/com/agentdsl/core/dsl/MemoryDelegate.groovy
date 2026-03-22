package com.agentdsl.core.dsl

import com.agentdsl.core.spec.MemoryConsolidationSpec
import com.agentdsl.core.spec.MemoryDecaySpec
import com.agentdsl.core.spec.MemoryLtmSpec
import com.agentdsl.core.spec.MemorySpec
import com.agentdsl.core.spec.MemoryStmSpec
import com.agentdsl.core.spec.MemoryVectorSpec

/**
 * Memory 块的委托类。
 * 支持 v1.5 的结构化子块，同时保留扁平 option 透传能力以兼容过渡期脚本。
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

    void deepRecallThreshold(Double threshold) {
        spec.deepRecallThreshold = threshold
    }

    void stm(@DelegatesTo(StmMemoryDelegate) Closure config) {
        def stmSpec = new MemoryStmSpec()
        def delegate = new StmMemoryDelegate(stmSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.stm = stmSpec
    }

    void ltm(@DelegatesTo(LtmMemoryDelegate) Closure config) {
        def ltmSpec = new MemoryLtmSpec()
        def delegate = new LtmMemoryDelegate(ltmSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.ltm = ltmSpec
    }

    void vector(@DelegatesTo(VectorMemoryDelegate) Closure config) {
        def vectorSpec = new MemoryVectorSpec()
        def delegate = new VectorMemoryDelegate(vectorSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.vector = vectorSpec
    }

    void decay(@DelegatesTo(DecayMemoryDelegate) Closure config) {
        def decaySpec = new MemoryDecaySpec()
        def delegate = new DecayMemoryDelegate(decaySpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.decay = decaySpec
    }

    void consolidation(@DelegatesTo(ConsolidationMemoryDelegate) Closure config) {
        def consolidationSpec = new MemoryConsolidationSpec()
        def delegate = new ConsolidationMemoryDelegate(consolidationSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.consolidation = consolidationSpec
    }

    void option(String key, Object value) {
        spec.putOption(key, value)
    }

    def methodMissing(String name, Object args) {
        Object[] values = normalizeArgs(args)
        if (values.length == 1) {
            spec.putOption(name, values[0])
            return null
        }
        if (values.length > 1) {
            spec.putOption(name, values.toList())
            return null
        }
        throw new MissingMethodException(name, this.class, values)
    }

    private static Object[] normalizeArgs(Object args) {
        if (args == null) {
            return new Object[0]
        }
        if (args instanceof Object[]) {
            return (Object[]) args
        }
        return [args] as Object[]
    }
}

class StmMemoryDelegate {

    private final MemoryStmSpec spec

    StmMemoryDelegate(MemoryStmSpec spec) {
        this.spec = spec
    }

    void maxEdges(Integer maxEdges) {
        spec.maxEdges = maxEdges
    }

    void ttlHours(Integer ttlHours) {
        spec.ttlHours = ttlHours
    }
}

class LtmMemoryDelegate {

    private final MemoryLtmSpec spec

    LtmMemoryDelegate(MemoryLtmSpec spec) {
        this.spec = spec
    }

    void backend(String backend) {
        spec.backend = backend
    }

    void path(String path) {
        spec.path = path
    }

    void compressionModel(String compressionModel) {
        spec.compressionModel = compressionModel
    }
}

class VectorMemoryDelegate {

    private final MemoryVectorSpec spec

    VectorMemoryDelegate(MemoryVectorSpec spec) {
        this.spec = spec
    }

    void store(String store) {
        spec.store = store
    }

    void embeddingModel(String embeddingModel) {
        spec.embeddingModel = embeddingModel
    }

    void path(String path) {
        spec.path = path
    }
}

class DecayMemoryDelegate {

    private final MemoryDecaySpec spec

    DecayMemoryDelegate(MemoryDecaySpec spec) {
        this.spec = spec
    }

    void baseRate(Double baseRate) {
        spec.baseRate = baseRate
    }

    void importanceBoost(Double importanceBoost) {
        spec.importanceBoost = importanceBoost
    }

    void compressionThreshold(Double compressionThreshold) {
        spec.compressionThreshold = compressionThreshold
    }

    void archiveThreshold(Double archiveThreshold) {
        spec.archiveThreshold = archiveThreshold
    }
}

class ConsolidationMemoryDelegate {

    private final MemoryConsolidationSpec spec

    ConsolidationMemoryDelegate(MemoryConsolidationSpec spec) {
        this.spec = spec
    }

    void intervalHours(Integer intervalHours) {
        spec.intervalHours = intervalHours
    }
}
