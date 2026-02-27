package com.agentdsl.runtime;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import com.agentdsl.langchain4j.LangChainMemoryFactory;
import com.agentdsl.langchain4j.LangChainModelFactory;
import com.agentdsl.langchain4j.LangChainRagFactory;
import com.agentdsl.langchain4j.LangChainToolBridge;
import com.agentdsl.langchain4j.LangChainToolBridge.ToolEntry;
import com.agentdsl.mcp.McpToolProviderBridge;
import com.agentdsl.mcp.McpToolProviderBridge.McpToolsResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心。
 * 管理所有已注册 Agent 的生命周期。
 */
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final ConcurrentHashMap<String, AgentInstance> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ToolSpec> globalTools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkflowSpec> workflows = new ConcurrentHashMap<>();

    private final LangChainModelFactory modelFactory;
    private final LangChainMemoryFactory memoryFactory;
    private final LangChainToolBridge toolBridge;
    private final LangChainRagFactory ragFactory;
    private final McpToolProviderBridge mcpBridge;
    private final List<McpToolsResult> mcpConnections = new ArrayList<>();

    public AgentRegistry() {
        this(new LangChainModelFactory(), new LangChainMemoryFactory(),
                new LangChainToolBridge(), new LangChainRagFactory());
    }

    public AgentRegistry(LangChainModelFactory modelFactory,
            LangChainMemoryFactory memoryFactory,
            LangChainToolBridge toolBridge,
            LangChainRagFactory ragFactory) {
        this.modelFactory = modelFactory;
        this.memoryFactory = memoryFactory;
        this.toolBridge = toolBridge;
        this.ragFactory = ragFactory;
        this.mcpBridge = new McpToolProviderBridge();
    }

    /**
     * 注册全局工具（可被多个 Agent 通过 include 引用）。
     */
    public void registerTool(ToolSpec tool) {
        log.info("注册全局工具: {}", tool.getName());
        globalTools.put(tool.getName(), tool);
    }

    /**
     * 批量注册全局工具。
     */
    public void registerTools(List<ToolSpec> tools) {
        for (ToolSpec tool : tools) {
            registerTool(tool);
        }
    }

    /**
     * 注册 Agent。
     * 将 AgentSpec 转换为 AgentInstance，创建所有 LangChain4j 组件。
     */
    public AgentInstance register(AgentSpec agentSpec) {
        log.info("注册 Agent: {}", agentSpec.getName());

        // 1. 创建模型
        ChatModel model = modelFactory.create(agentSpec.getModel());

        // 2. 创建记忆
        ChatMemory memory = memoryFactory.create(agentSpec.getMemory());

        // 3. 收集工具
        List<ToolSpecification> toolSpecifications = new ArrayList<>();
        Map<String, ToolExecutor> toolExecutors = new HashMap<>();

        // 内联定义的工具
        if (agentSpec.getTools() != null) {
            for (ToolSpec toolSpec : agentSpec.getTools()) {
                ToolEntry entry = toolBridge.convert(toolSpec);
                toolSpecifications.add(entry.specification());
                toolExecutors.put(entry.specification().name(), entry.executor());
            }
        }

        // 通过 include 引用的全局工具
        if (agentSpec.getToolRefs() != null) {
            for (String ref : agentSpec.getToolRefs()) {
                ToolSpec globalTool = globalTools.get(ref);
                if (globalTool == null) {
                    throw new DslRuntimeException("ADSL-011",
                            "Agent '" + agentSpec.getName() + "' 引用了未注册的工具: " + ref);
                }
                ToolEntry entry = toolBridge.convert(globalTool);
                toolSpecifications.add(entry.specification());
                toolExecutors.put(entry.specification().name(), entry.executor());
            }
        }

        // 4. 创建 RAG ContentRetriever（如果配置了 RAG）
        ContentRetriever contentRetriever = null;
        if (agentSpec.getRag() != null) {
            contentRetriever = ragFactory.create(agentSpec.getRag());
            log.info("Agent '{}' 已配置 RAG", agentSpec.getName());
        }

        // 5. 处理 MCP 工具
        if (agentSpec.getMcp() != null && !agentSpec.getMcp().getServers().isEmpty()) {
            try {
                McpToolsResult result = mcpBridge.connect(agentSpec.getMcp());
                mcpConnections.add(result);
                toolSpecifications.addAll(result.toolSpecifications());
                toolExecutors.putAll(result.toolExecutors());
                log.info("Agent '{}' 加载了 {} 个 MCP 工具",
                        agentSpec.getName(), result.toolSpecifications().size());
            } catch (Exception e) {
                log.error("Agent '{}' MCP 连接失败: {}", agentSpec.getName(), e.getMessage(), e);
                throw new DslRuntimeException("ADSL-050",
                        "MCP 连接失败: " + e.getMessage(), e);
            }
        }

        // 6. 组装实例
        AgentInstance instance = new AgentInstance(
                agentSpec, model, memory, toolSpecifications, toolExecutors, contentRetriever);

        agents.put(agentSpec.getName(), instance);
        log.info("Agent '{}' 注册成功: {}", agentSpec.getName(), instance);
        return instance;
    }

    /**
     * 获取已注册的 Agent 实例。
     */
    public AgentInstance get(String name) {
        AgentInstance instance = agents.get(name);
        if (instance == null) {
            throw new DslRuntimeException("ADSL-010",
                    "未找到 Agent: " + name + "。已注册的 Agent: " + agents.keySet());
        }
        return instance;
    }

    /**
     * 检查 Agent 是否已注册。
     */
    public boolean has(String name) {
        return agents.containsKey(name);
    }

    /**
     * 获取所有已注册的 Agent 名称。
     */
    public Set<String> getAgentNames() {
        return Collections.unmodifiableSet(agents.keySet());
    }

    /**
     * 注销 Agent。
     */
    public void unregister(String name) {
        AgentInstance removed = agents.remove(name);
        if (removed != null) {
            log.info("Agent '{}' 已注销", name);
        }
    }

    // --- Workflow 注册 ---

    /**
     * 注册工作流。
     */
    public void registerWorkflow(WorkflowSpec workflow) {
        log.info("注册 Workflow: {}", workflow.getName());
        workflows.put(workflow.getName(), workflow);
    }

    /**
     * 批量注册工作流。
     */
    public void registerWorkflows(List<WorkflowSpec> workflowList) {
        for (WorkflowSpec workflow : workflowList) {
            registerWorkflow(workflow);
        }
    }

    /**
     * 获取已注册的工作流。
     */
    public WorkflowSpec getWorkflow(String name) {
        WorkflowSpec workflow = workflows.get(name);
        if (workflow == null) {
            throw new DslRuntimeException("ADSL-011",
                    "未找到 Workflow: " + name + "。已注册的 Workflow: " + workflows.keySet());
        }
        return workflow;
    }

    /**
     * 检查工作流是否已注册。
     */
    public boolean hasWorkflow(String name) {
        return workflows.containsKey(name);
    }

    /**
     * 获取所有已注册的工作流名称。
     */
    public Set<String> getWorkflowNames() {
        return Collections.unmodifiableSet(workflows.keySet());
    }

    /**
     * 清除所有注册。
     */
    public void clear() {
        agents.clear();
        globalTools.clear();
        workflows.clear();
        log.info("注册中心已清空");
    }

    /**
     * 关闭所有 MCP 连接。
     */
    public void closeMcpConnections() {
        for (McpToolsResult conn : mcpConnections) {
            try {
                conn.close();
            } catch (Exception e) {
                log.warn("关闭 MCP 连接异常: {}", e.getMessage());
            }
        }
        mcpConnections.clear();
        log.info("所有 MCP 连接已关闭");
    }
}
