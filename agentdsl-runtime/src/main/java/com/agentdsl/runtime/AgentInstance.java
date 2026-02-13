package com.agentdsl.runtime;

import com.agentdsl.core.spec.AgentSpec;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 一个已注册的 Agent 运行时实例。
 * 持有原始 DSL 规范和对应的 LangChain4j 实例。
 */
public class AgentInstance {

    private final AgentSpec spec;
    private final ChatModel model;
    private final ChatMemory memory;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Instant registeredAt;

    public AgentInstance(AgentSpec spec,
            ChatModel model,
            ChatMemory memory,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors) {
        this.spec = spec;
        this.model = model;
        this.memory = memory;
        this.toolSpecifications = toolSpecifications;
        this.toolExecutors = toolExecutors;
        this.registeredAt = Instant.now();
    }

    public AgentSpec getSpec() {
        return spec;
    }

    public String getName() {
        return spec.getName();
    }

    public ChatModel getModel() {
        return model;
    }

    public ChatMemory getMemory() {
        return memory;
    }

    public List<ToolSpecification> getToolSpecifications() {
        return toolSpecifications;
    }

    public Map<String, ToolExecutor> getToolExecutors() {
        return toolExecutors;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public boolean hasTools() {
        return toolSpecifications != null && !toolSpecifications.isEmpty();
    }

    @Override
    public String toString() {
        return "AgentInstance{name='" + spec.getName()
                + "', model=" + spec.getModel().getProvider() + "/" + spec.getModel().getModelName()
                + ", tools=" + (toolSpecifications != null ? toolSpecifications.size() : 0)
                + ", registeredAt=" + registeredAt + '}';
    }
}
