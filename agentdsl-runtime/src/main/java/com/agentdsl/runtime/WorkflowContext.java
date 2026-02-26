package com.agentdsl.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流执行上下文。
 * 管理步骤间的数据传递和结果收集。
 */
public class WorkflowContext {

    /** 上一个步骤的输出 */
    private Object lastOutput;

    /** 所有步骤的命名结果 */
    private final Map<String, Object> stepResults = new LinkedHashMap<>();

    /** 初始输入 */
    private final String initialInput;

    public WorkflowContext(String initialInput) {
        this.initialInput = initialInput;
        this.lastOutput = initialInput;
    }

    /**
     * 获取上一个步骤的输出。
     */
    public Object getLastOutput() {
        return lastOutput;
    }

    /**
     * 设置最新的输出（内部使用）。
     */
    public void setLastOutput(Object output) {
        this.lastOutput = output;
    }

    /**
     * 记录某个步骤的输出结果。
     */
    public void putStepResult(String stepName, Object result) {
        stepResults.put(stepName, result);
        this.lastOutput = result;
    }

    /**
     * 获取某个步骤的输出结果。
     */
    public Object getStepResult(String stepName) {
        return stepResults.get(stepName);
    }

    /**
     * 获取所有步骤结果的只读视图。
     */
    public Map<String, Object> getAllStepResults() {
        return Collections.unmodifiableMap(stepResults);
    }

    /**
     * 获取初始输入。
     */
    public String getInitialInput() {
        return initialInput;
    }

    /**
     * 转换为最终结果。
     */
    public WorkflowResult toResult() {
        return new WorkflowResult(lastOutput, stepResults);
    }
}
