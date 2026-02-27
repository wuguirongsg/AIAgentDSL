package com.agentdsl.core.dsl

import com.agentdsl.core.spec.PermissionSpec

class PermissionDelegate {

    private final PermissionSpec spec

    PermissionDelegate(PermissionSpec spec) {
        this.spec = spec
    }

    void network(String... patterns) {
        patterns.each { spec.addNetworkPattern(it) }
    }

    void file(String... patterns) {
        patterns.each { spec.addFilePattern(it) }
    }

    void database(String... dbs) {
        dbs.each { spec.addDatabase(it) }
    }

}
