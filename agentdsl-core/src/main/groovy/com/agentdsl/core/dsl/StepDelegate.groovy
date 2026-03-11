package com.agentdsl.core.dsl

import com.agentdsl.core.spec.StepSpec

/**
 * step("name") { ... } 闭包的委托。
 * 解析 agent、execute、tool、skill、mcp、input、output 关键字。
 *
 * 五种执行模式互斥：agent / execute / tool / skill / mcp
 */
class StepDelegate {

    final StepSpec spec

    StepDelegate(StepSpec spec) {
        this.spec = spec
    }

    /**
     * agent "agent-name" — 认知节点，调用 Agent（大模型）执行
     */
    void agent(String agentName) {
        spec.agentRef = agentName
    }

    /**
     * execute { ctx -> ... } — 执行节点，纯代码执行（0 LLM 参与）
     * ctx 为 WorkflowExecutionContext，可调用 ctx.toolCall() / ctx.mcpCall()
     */
    void execute(Closure<?> closure) {
        spec.executeClosure = closure
    }

    /**
     * tool "tool-name" — 工具节点，直接调用已注册工具（跳过 LLM）
     * 配合 input 闭包提供工具参数（返回 Map）
     */
    void tool(String toolName) {
        spec.toolRef = toolName
    }

    /**
     * skill "skill-name" — 技能节点，直接调用已注册 Skill（跳过 LLM）
     * 配合 input 闭包提供 Skill 参数（返回 Map）
     */
    void skill(String skillName) {
        spec.skillRef = skillName
    }

    /**
     * mcp "server-name", "tool-name" — MCP 节点，直接调用 MCP 工具（跳过 LLM）
     * 配合 input 闭包提供工具参数（返回 Map）
     */
    void mcp(String serverName, String toolName) {
        spec.mcpServerRef = serverName
        spec.mcpToolRef = toolName
    }

    /**
     * input { prevResult -> ... } — 输入转换闭包
     * agent 模式下返回 String（聊天消息），tool/skill/mcp 模式下返回 Map（工具参数）
     */
    void input(Closure<?> transform) {
        spec.inputTransform = transform
    }

    /**
     * output { result -> ... } — 输出转换闭包
     */
    void output(Closure<?> transform) {
        spec.outputTransform = transform
    }
}
