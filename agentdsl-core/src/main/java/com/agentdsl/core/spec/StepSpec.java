package com.agentdsl.core.spec;

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流步骤规范模型。
 * 使用 StepType 枚举区分不同类型的步骤：顺序、并行、条件、循环。
 *
 * 对应 DSL 中的：
 * - step("name") { agent "xxx"; input { ... }; output { ... } }
 * - parallel { step(...) ... }
 * - condition { check { ... }; on "value" { ... } }
 * - loop(maxIterations: N) { step(...); until { ... }; step(...) }
 */
public class StepSpec {

    /**
     * 步骤类型枚举。
     */
    public enum StepType {
        /** 顺序执行步骤 */
        SEQUENTIAL,
        /** 并行执行一组步骤 */
        PARALLEL,
        /** 条件路由分支 */
        CONDITION,
        /** 循环迭代 */
        LOOP
    }

    // --- 通用字段 ---
    private StepType type;
    private String name;

    // --- SEQUENTIAL 字段 ---
    /** 引用的 Agent 名称（认知节点，与 executeClosure/toolRef/skillRef/mcpServerRef 互斥） */
    private String agentRef;
    /** 纯代码执行闭包（执行节点，与 agentRef/toolRef/skillRef/mcpServerRef 互斥） */
    private Closure<?> executeClosure;
    /** 直接调用的工具名称（工具节点，与 agentRef/executeClosure/skillRef/mcpServerRef 互斥） */
    private String toolRef;
    /** 直接调用的 Skill 名称（技能节点，与 agentRef/executeClosure/toolRef/mcpServerRef 互斥） */
    private String skillRef;
    /** 直接调用的 MCP 服务器名称（MCP 节点，与 agentRef/executeClosure/toolRef/skillRef 互斥） */
    private String mcpServerRef;
    /** 直接调用的 MCP 工具名称 */
    private String mcpToolRef;
    /** 输入转换闭包 */
    private Closure<?> inputTransform;
    /** 输出转换闭包 */
    private Closure<?> outputTransform;

    // --- PARALLEL 字段 ---
    /** 并行执行的步骤列表 */
    private List<StepSpec> parallelSteps = new ArrayList<>();

    // --- CONDITION 字段 ---
    /** 条件判断闭包 */
    private Closure<?> checkClosure;
    /** 条件分支：条件值 -> 步骤列表 */
    private Map<String, List<StepSpec>> branches = new LinkedHashMap<>();

    // --- LOOP 字段 ---
    /** 最大循环次数 */
    private int maxIterations = 10;
    /** 循环终止条件闭包 */
    private Closure<?> untilClosure;
    /** 循环体步骤列表 */
    private List<StepSpec> loopBody = new ArrayList<>();

    public StepSpec() {
    }

    public StepSpec(StepType type) {
        this.type = type;
    }

    public StepSpec(StepType type, String name) {
        this.type = type;
        this.name = name;
    }

    // --- Getters & Setters ---

    public StepType getType() {
        return type;
    }

    public void setType(StepType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAgentRef() {
        return agentRef;
    }

    public void setAgentRef(String agentRef) {
        this.agentRef = agentRef;
    }

    public Closure<?> getExecuteClosure() {
        return executeClosure;
    }

    public void setExecuteClosure(Closure<?> executeClosure) {
        this.executeClosure = executeClosure;
    }

    public String getToolRef() {
        return toolRef;
    }

    public void setToolRef(String toolRef) {
        this.toolRef = toolRef;
    }

    public String getSkillRef() {
        return skillRef;
    }

    public void setSkillRef(String skillRef) {
        this.skillRef = skillRef;
    }

    public String getMcpServerRef() {
        return mcpServerRef;
    }

    public void setMcpServerRef(String mcpServerRef) {
        this.mcpServerRef = mcpServerRef;
    }

    public String getMcpToolRef() {
        return mcpToolRef;
    }

    public void setMcpToolRef(String mcpToolRef) {
        this.mcpToolRef = mcpToolRef;
    }

    /**
     * 判断该步骤是否为直接执行模式（非 Agent 认知节点）。
     */
    public boolean isDirectExecution() {
        return executeClosure != null || toolRef != null || skillRef != null || mcpServerRef != null;
    }

    /**
     * 获取执行模式的描述字符串（用于日志和追踪）。
     */
    public String getExecutionMode() {
        if (executeClosure != null) return "execute";
        if (toolRef != null) return "tool:" + toolRef;
        if (skillRef != null) return "skill:" + skillRef;
        if (mcpServerRef != null) return "mcp:" + mcpServerRef + "/" + mcpToolRef;
        if (agentRef != null) return "agent:" + agentRef;
        return "undefined";
    }

    public Closure<?> getInputTransform() {
        return inputTransform;
    }

    public void setInputTransform(Closure<?> inputTransform) {
        this.inputTransform = inputTransform;
    }

    public Closure<?> getOutputTransform() {
        return outputTransform;
    }

    public void setOutputTransform(Closure<?> outputTransform) {
        this.outputTransform = outputTransform;
    }

    public List<StepSpec> getParallelSteps() {
        return parallelSteps;
    }

    public void setParallelSteps(List<StepSpec> parallelSteps) {
        this.parallelSteps = parallelSteps;
    }

    public Closure<?> getCheckClosure() {
        return checkClosure;
    }

    public void setCheckClosure(Closure<?> checkClosure) {
        this.checkClosure = checkClosure;
    }

    public Map<String, List<StepSpec>> getBranches() {
        return branches;
    }

    public void setBranches(Map<String, List<StepSpec>> branches) {
        this.branches = branches;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public Closure<?> getUntilClosure() {
        return untilClosure;
    }

    public void setUntilClosure(Closure<?> untilClosure) {
        this.untilClosure = untilClosure;
    }

    public List<StepSpec> getLoopBody() {
        return loopBody;
    }

    public void setLoopBody(List<StepSpec> loopBody) {
        this.loopBody = loopBody;
    }

    @Override
    public String toString() {
        return switch (type) {
            case SEQUENTIAL -> "Step{name='" + name + "', mode=" + getExecutionMode() + "}";
            case PARALLEL -> "Parallel{steps=" + parallelSteps.size() + "}";
            case CONDITION -> "Condition{branches=" + branches.keySet() + "}";
            case LOOP -> "Loop{maxIterations=" + maxIterations + ", body=" + loopBody.size() + "}";
        };
    }
}
