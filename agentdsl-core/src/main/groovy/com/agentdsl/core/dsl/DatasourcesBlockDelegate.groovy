package com.agentdsl.core.dsl

import com.agentdsl.core.spec.AgentSpec

class DatasourcesBlockDelegate {

    private final AgentSpec spec

    DatasourcesBlockDelegate(AgentSpec spec) {
        this.spec = spec
    }

    void attach(String datasourceName) {
        if (!spec.datasourceRefs.contains(datasourceName)) {
            spec.datasourceRefs.add(datasourceName)
        }
    }

    void attach(String... datasourceNames) {
        for (name in datasourceNames) {
            attach(name)
        }
    }

}
