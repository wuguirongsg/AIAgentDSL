package com.agentdsl.core.dsl

import com.agentdsl.core.spec.DataSourceSpec

class DataSourceDelegate {

    private final DataSourceSpec spec

    DataSourceDelegate(DataSourceSpec spec) {
        this.spec = spec
    }

    void type(String type) {
        spec.type = type
    }

    void url(String url) {
        spec.url = url
    }

    void username(String username) {
        spec.username = username
    }

    void password(String password) {
        spec.password = password
    }

    void maxConnections(int maxConnections) {
        spec.maxConnections = maxConnections
    }

}
