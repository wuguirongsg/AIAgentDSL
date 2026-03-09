package com.agentdsl.core.dsl

import com.agentdsl.core.spec.SearchSpec

class SearchDelegate {

    private final SearchSpec spec

    SearchDelegate(SearchSpec spec) {
        this.spec = spec
    }

    void provider(String provider) {
        spec.provider = provider
    }

    void apiKey(String apiKey) {
        spec.apiKey = apiKey
    }

    void maxResults(Integer maxResults) {
        spec.maxResults = maxResults
    }

}
