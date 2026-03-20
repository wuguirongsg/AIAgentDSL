package com.agentdsl.runtime.autonomous.model;

import java.util.Collections;
import java.util.List;

/**
 * 每步 ReAct 执行的上下文快照，提供给 ExecutionMonitor 分析用。
 */
public class StepContext {

    /** 单次工具调用的摘要信息 */
    public record ToolCall(
            String name,
            int argsHash,    // 参数哈希（停滞检测用，无需暴露原始参数）
            String result    // 工具执行结果文本
    ) {}

    private final String llmOutput;         // AI 本步输出文本（用于置信度推断）
    private final List<ToolCall> toolsCalled;
    private final int tokensUsedThisStep;   // 本步消耗的 token 估算值

    public StepContext(String llmOutput,
                       List<ToolCall> toolsCalled,
                       int tokensUsedThisStep) {
        this.llmOutput = llmOutput;
        this.toolsCalled = toolsCalled == null
                ? List.of() : Collections.unmodifiableList(toolsCalled);
        this.tokensUsedThisStep = tokensUsedThisStep;
    }

    public String getLlmOutput() { return llmOutput; }
    public List<ToolCall> getToolsCalled() { return toolsCalled; }
    public int getTokensUsedThisStep() { return tokensUsedThisStep; }

    /**
     * 生成本步行动的指纹（用于停滞检测）。
     * 相同工具+相同参数 → 相同指纹 → 停滞。
     */
    public String actionFingerprint() {
        if (toolsCalled.isEmpty()) return "NO_TOOL";
        return toolsCalled.stream()
                .map(t -> t.name() + ":" + t.argsHash())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "|" + b);
    }

    /**
     * 检查本步是否有实质性进展（工具调用成功）。
     */
    public boolean hasProgress() {
        return toolsCalled.stream()
                .anyMatch(t -> !t.result().startsWith("Error")
                        && !t.result().startsWith("[工具失败"));
    }
}
