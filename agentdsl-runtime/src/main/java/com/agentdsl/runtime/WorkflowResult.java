package com.agentdsl.runtime;

import java.util.Collections;
import java.util.Map;

/**
 * 工作流执行结果。
 */
public class WorkflowResult {

    /** 最终输出 */
    private final Object finalOutput;

    /** 所有步骤的命名结果 */
    private final Map<String, Object> stepResults;

    public WorkflowResult(Object finalOutput, Map<String, Object> stepResults) {
        this.finalOutput = finalOutput;
        this.stepResults = Collections.unmodifiableMap(stepResults);
    }

    /**
     * 获取最终输出。
     */
    public Object getFinalOutput() {
        return finalOutput;
    }

    /**
     * 获取最终输出的字符串形式。
     */
    public String getFinalOutputAsString() {
        return finalOutput != null ? finalOutput.toString() : null;
    }

    /**
     * 获取所有步骤结果。
     */
    public Map<String, Object> getStepResults() {
        return stepResults;
    }

    /**
     * 获取指定步骤的结果。
     */
    public Object getStepResult(String stepName) {
        return stepResults.get(stepName);
    }

    @Override
    public String toString() {
        return "WorkflowResult{" +
                "finalOutput=" + (finalOutput != null ? finalOutput.toString().substring(0,
                        Math.min(100, finalOutput.toString().length())) : "null")
                +
                ", steps=" + stepResults.keySet() +
                '}';
    }
}
