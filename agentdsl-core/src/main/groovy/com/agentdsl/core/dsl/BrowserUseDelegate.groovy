package com.agentdsl.core.dsl

import com.agentdsl.core.spec.BrowserUseSpec

import java.util.Arrays

/**
 * Browser Use 委托类.
 * 处理 browser_use { ... } 内部所有的关键字.
 */
public class BrowserUseDelegate {

    private final BrowserUseSpec spec

    public BrowserUseDelegate(BrowserUseSpec spec) {
        this.spec = spec
    }

    /** sandbox true */
    public void sandbox(boolean sandbox) {
        spec.setSandbox(sandbox)
    }

    /** hitl_on "tool1", "tool2" */
    public void hitl_on(String... actions) {
        spec.getHitlActions().addAll(Arrays.asList(actions))
    }

    public BrowserUseSpec getSpec() {
        return spec
    }

}
