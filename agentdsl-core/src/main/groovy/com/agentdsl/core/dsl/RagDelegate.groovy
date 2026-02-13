package com.agentdsl.core.dsl

import com.agentdsl.core.spec.ContentRetrieverSpec
import com.agentdsl.core.spec.RagSpec

/**
 * RAG 块的委托类。
 */
class RagDelegate {

    private final RagSpec spec

    RagDelegate(RagSpec spec) {
        this.spec = spec
    }

    void contentRetriever(@DelegatesTo(ContentRetrieverDelegate) Closure config) {
        def retrieverSpec = new ContentRetrieverSpec()
        def delegate = new ContentRetrieverDelegate(retrieverSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.contentRetriever = retrieverSpec
    }
}

/**
 * ContentRetriever 块的委托类。
 */
class ContentRetrieverDelegate {

    private final ContentRetrieverSpec spec

    ContentRetrieverDelegate(ContentRetrieverSpec spec) {
        this.spec = spec
    }

    void type(String type) {
        spec.type = type
    }

    void embeddingModel(String embeddingModel) {
        spec.embeddingModel = embeddingModel
    }

    void maxResults(Integer maxResults) {
        spec.maxResults = maxResults
    }

    void minScore(Double minScore) {
        spec.minScore = minScore
    }
}
