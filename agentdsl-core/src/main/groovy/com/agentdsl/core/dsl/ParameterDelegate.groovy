package com.agentdsl.core.dsl

import com.agentdsl.core.spec.ParameterSpec

/**
 * Parameter 块的委托类。
 * 处理 parameter { ... } 内部的关键字。
 */
class ParameterDelegate {

    private final ParameterSpec spec

    ParameterDelegate(ParameterSpec spec) {
        this.spec = spec
    }

    void name(String name) {
        spec.name = name
    }

    void type(String type) {
        spec.type = type
    }

    void description(String description) {
        spec.description = description
    }

    void required(boolean required) {
        spec.required = required
    }

    void pattern(String regex) {
        spec.pattern = regex
    }

    void defaultValue(Object value) {
        spec.defaultValue = value
    }

    void enumValues(String... values) {
        spec.enumValues = values.join(',')
    }

    void min(double value) {
        spec.min = value
    }

    void max(double value) {
        spec.max = value
    }

}
