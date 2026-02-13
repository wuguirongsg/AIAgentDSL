package com.agentdsl.core.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 定义的顶层规范模型。
 * 对应 DSL 中的 agent("name") { ... } 块。
 */
public class AgentSpec {

    private String name;
    private String description;
    private ModelSpec model;
    private String systemPrompt;
    private MemorySpec memory;
    private List<ToolSpec> tools = new ArrayList<>();
    private List<String> toolRefs = new ArrayList<>();
    private RagSpec rag;
    private GuardrailSpec guardrails;
    private OutputSchemaSpec outputSchema;

    public AgentSpec() {
    }

    public AgentSpec(String name) {
        this.name = name;
    }

    // --- Getters & Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ModelSpec getModel() {
        return model;
    }

    public void setModel(ModelSpec model) {
        this.model = model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public MemorySpec getMemory() {
        return memory;
    }

    public void setMemory(MemorySpec memory) {
        this.memory = memory;
    }

    public List<ToolSpec> getTools() {
        return tools;
    }

    public void setTools(List<ToolSpec> tools) {
        this.tools = tools;
    }

    public List<String> getToolRefs() {
        return toolRefs;
    }

    public void setToolRefs(List<String> toolRefs) {
        this.toolRefs = toolRefs;
    }

    public RagSpec getRag() {
        return rag;
    }

    public void setRag(RagSpec rag) {
        this.rag = rag;
    }

    public GuardrailSpec getGuardrails() {
        return guardrails;
    }

    public void setGuardrails(GuardrailSpec guardrails) {
        this.guardrails = guardrails;
    }

    public OutputSchemaSpec getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(OutputSchemaSpec outputSchema) {
        this.outputSchema = outputSchema;
    }

    @Override
    public String toString() {
        return "AgentSpec{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", model=" + model +
                ", systemPrompt='"
                + (systemPrompt != null ? systemPrompt.substring(0, Math.min(50, systemPrompt.length())) + "..."
                        : "null")
                + '\'' +
                ", memory=" + memory +
                ", tools=" + tools.size() + " inline + " + toolRefs.size() + " refs" +
                '}';
    }
}
