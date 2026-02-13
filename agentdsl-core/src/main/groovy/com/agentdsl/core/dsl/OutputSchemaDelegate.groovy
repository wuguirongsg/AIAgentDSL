package com.agentdsl.core.dsl

import com.agentdsl.core.spec.OutputSchemaSpec

/**
 * OutputSchema 块的委托类。
 */
class OutputSchemaDelegate {

    private final OutputSchemaSpec spec

    OutputSchemaDelegate(OutputSchemaSpec spec) {
        this.spec = spec
    }

    /** field "name", "type", "description" */
    void field(String name, String type, String description) {
        spec.addField(name, type, description)
    }
}
