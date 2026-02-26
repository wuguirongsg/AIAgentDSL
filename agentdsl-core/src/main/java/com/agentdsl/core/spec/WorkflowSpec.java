package com.agentdsl.core.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流定义的顶层规范模型。
 * 对应 DSL 中的 workflow("name") { ... } 块。
 */
public class WorkflowSpec {

    private String name;
    private String description;
    private List<StepSpec> steps = new ArrayList<>();

    public WorkflowSpec() {
    }

    public WorkflowSpec(String name) {
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

    public List<StepSpec> getSteps() {
        return steps;
    }

    public void setSteps(List<StepSpec> steps) {
        this.steps = steps;
    }

    @Override
    public String toString() {
        return "WorkflowSpec{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", steps=" + steps.size() +
                '}';
    }
}
