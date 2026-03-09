package com.agentdsl.core.dsl

import com.agentdsl.core.spec.*

/**
 * Agent 块的委托类。
 * 处理 agent("name") { ... } 内部的所有关键字。
 */
class AgentDelegate {

    private final AgentSpec spec

    AgentDelegate(AgentSpec spec) {
        this.spec = spec
    }

    /** description "描述文本" */
    void description(String desc) {
        spec.description = desc
    }

    /** systemPrompt "提示词" 或 systemPrompt '''多行提示词''' */
    void systemPrompt(String prompt) {
        spec.systemPrompt = prompt
    }

    /** model { ... } */
    void model(@DelegatesTo(ModelDelegate) Closure config) {
        def modelSpec = new ModelSpec()
        def delegate = new ModelDelegate(modelSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.model = modelSpec
    }

    /** memory { ... } */
    void memory(@DelegatesTo(MemoryDelegate) Closure config) {
        def memorySpec = new MemorySpec()
        def delegate = new MemoryDelegate(memorySpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.memory = memorySpec
    }

    /** tools { ... } */
    void tools(@DelegatesTo(ToolsBlockDelegate) Closure config) {
        def delegate = new ToolsBlockDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }

    /** rag { ... } */
    void rag(@DelegatesTo(RagDelegate) Closure config) {
        def ragSpec = new RagSpec()
        def delegate = new RagDelegate(ragSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.rag = ragSpec
    }

    /** guardrails { ... } */
    void guardrails(@DelegatesTo(GuardrailDelegate) Closure config) {
        def guardrailSpec = new GuardrailSpec()
        def delegate = new GuardrailDelegate(guardrailSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.guardrails = guardrailSpec
    }

    /** outputSchema { ... } */
    void outputSchema(@DelegatesTo(OutputSchemaDelegate) Closure config) {
        def schemaSpec = new OutputSchemaSpec()
        def delegate = new OutputSchemaDelegate(schemaSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.outputSchema = schemaSpec
    }

    /** mcp { server("name") { ... } } */
    void mcp(@DelegatesTo(McpBlockDelegate) Closure config) {
        def delegate = new McpBlockDelegate()
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.mcp = delegate.spec
    }

    /**
     * skills { include "skill-name" }
     * 引用已通过顶层 skill("name") { ... } 定义的全局技能。
     * 运行时将从 globalSkills 仓库中查找并展平为 LangChain4j ToolSpecification。
     */
    void skills(@DelegatesTo(SkillsBlockDelegate) Closure config) {
        def delegate = new SkillsBlockDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }

    void datasources(@DelegatesTo(DatasourcesBlockDelegate) Closure config) {
        def delegate = new DatasourcesBlockDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }

    /** search { provider "tavily"; apiKey env("XXX") } */
    void search(@DelegatesTo(SearchDelegate) Closure config) {
        def searchSpec = new SearchSpec()
        def delegate = new SearchDelegate(searchSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.searchConfig = searchSpec
    }

    /** browser_use { server "@microsoft/playwright-mcp" } */
    void browser_use(@DelegatesTo(BrowserUseDelegate) Closure config) {
        def browserUseSpec = new BrowserUseSpec()
        def delegate = new BrowserUseDelegate(browserUseSpec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        spec.browserUse = browserUseSpec
        // 隐式配置 MCP 被移除，改用 NativeBrowserTool
    }

}
