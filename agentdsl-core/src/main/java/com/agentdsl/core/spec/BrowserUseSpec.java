package com.agentdsl.core.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * Browser Use 配置模型.
 * 映射: browser_use { ... }
 */
public class BrowserUseSpec {

    private boolean sandbox = false;
    private List<String> hitlActions = new ArrayList<>();

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public List<String> getHitlActions() {
        return hitlActions;
    }

    public void setHitlActions(List<String> hitlActions) {
        this.hitlActions = hitlActions;
    }

    public String toString() {
        return "BrowserUseSpec{" +
                "sandbox=" + sandbox +
                ", hitlActions=" + hitlActions +
                '}';
    }
}
