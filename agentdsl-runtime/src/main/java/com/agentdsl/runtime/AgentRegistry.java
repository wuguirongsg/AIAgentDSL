package com.agentdsl.runtime;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import com.agentdsl.langchain4j.LangChainMemoryFactory;
import com.agentdsl.langchain4j.LangChainModelFactory;
import com.agentdsl.langchain4j.LangChainRagFactory;
import com.agentdsl.langchain4j.LangChainToolBridge;
import com.agentdsl.langchain4j.LangChainToolBridge.ToolEntry;
import com.agentdsl.mcp.McpToolProviderBridge;
import com.agentdsl.mcp.McpToolProviderBridge.McpToolsResult;
import com.agentdsl.tools.ToolScanner;
import com.agentdsl.tools.builtin.NativeBrowserTool;
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
    private final ConcurrentHashMap<String, SkillSpec> globalSkills = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkflowSpec> workflows = new ConcurrentHashMap<>();

    private final LangChainModelFactory modelFactory;
    private final LangChainMemoryFactory memoryFactory;
    private final LangChainToolBridge toolBridge;
    private final LangChainRagFactory ragFactory;
    private final McpToolProviderBridge mcpBridge;
    private final List<McpToolsResult> mcpConnections = new ArrayList<>();
    private final List<NativeBrowserTool> activeBrowserTools = new ArrayList<>();

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

        // 注入 toolCall 解析器：允许 Logic Skill 闭包内调用已注册的全局工具
        this.toolBridge.setToolCallResolver((toolName, params) -> {
            ToolSpec tool = globalTools.get(toolName);
            if (tool == null) {
                return "Error: 工具 '" + toolName + "' 未注册。已注册工具: " + globalTools.keySet();
            }
            try {
                if (tool.isBeanMethod()) {
                    // @AgentTool 注解的 Java 方法：使用 ToolSpec 的 ParameterSpec 匹配参数
                    java.lang.reflect.Method method = tool.getToolBeanMethod();
                    Object bean = tool.getToolBean();
                    java.lang.reflect.Parameter[] methodParams = method.getParameters();
                    List<com.agentdsl.core.spec.ParameterSpec> paramSpecs = tool.getParameters();

                    Object[] args = new Object[methodParams.length];
                    for (int i = 0; i < methodParams.length; i++) {
                        // 优先用 ToolSpec 中 ParameterSpec 的名称（来自 @ToolParam 扫描）
                        String paramName = (paramSpecs != null && i < paramSpecs.size())
                                ? paramSpecs.get(i).getName()
                                : methodParams[i].getName();
                        Object val = params.get(paramName);
                        args[i] = com.agentdsl.core.utils.ConvertUtils.convertArg(val, methodParams[i].getType());
                        log.info(">>>> toolCall() debug: 匹配参数 i={}, name={}(from spec? {}), val={}, finalArg={}",
                                i, paramName, (paramSpecs != null && i < paramSpecs.size()), val, args[i]);
                    }
                    Object result = method.invoke(bean, args);
                    return result != null ? result.toString() : "null";
                }
                // DSL 内联闭包工具
                Object body = tool.getExecuteBody();
                if (body instanceof groovy.lang.Closure<?> closure) {
                    Object result = closure.getMaximumNumberOfParameters() == 0
                            ? closure.call()
                            : closure.call(params);
                    return result != null ? result.toString() : "null";
                }
                return "Error: 工具 '" + toolName + "' 没有可执行逻辑。";
            } catch (Exception e) {
                log.error("toolCall('{}') 执行失败", toolName, e);
                return "Error: toolCall('" + toolName + "') 执行失败: " + e.getMessage();
            }
        });
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
     * 注册全局 Skill。
     * Skill 保存在 globalSkills 中，在 Agent 注册时展平为 ToolSpec。
     */
    public void registerSkill(SkillSpec skill) {
        log.info("注册全局技能: {} [{}]", skill.getName(), skill.getType());
        globalSkills.put(skill.getName(), skill);
    }

    /**
     * 批量注册全局 Skill。
     */
    public void registerSkills(List<SkillSpec> skills) {
        for (SkillSpec skill : skills) {
            registerSkill(skill);
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

        // 展平全局 Skill 引用（include "skillName"）
        // - Logic Skill → 注册为可执行工具
        // - Prompt Skill → 注入到 systemPrompt（不暴露为工具，避免 LLM 误用）
        StringBuilder skillPromptAppend = new StringBuilder();
        if (agentSpec.getSkillRefs() != null) {
            for (String skillRef : agentSpec.getSkillRefs()) {
                SkillSpec skill = globalSkills.get(skillRef);
                if (skill == null) {
                    throw new DslRuntimeException("ADSL-011",
                            "Agent '" + agentSpec.getName() + "' 引用了未注册的技能: " + skillRef);
                }
                if (skill.isLogicSkill()) {
                    ToolSpec skillAsTool = flattenSkillToTool(skill);
                    ToolEntry entry = toolBridge.convert(skillAsTool);
                    toolSpecifications.add(entry.specification());
                    toolExecutors.put(entry.specification().name(), entry.executor());
                    log.info("Agent '{}' 注册 Logic Skill 为工具: {}", agentSpec.getName(), skill.getName());
                } else {
                    // Prompt Skill: 注入到 system prompt
                    skillPromptAppend.append("\n\n---\n### Skill: ").append(skill.getName())
                            .append("\n").append(skill.getInstruction());
                    log.info("Agent '{}' 注入 Prompt Skill 到 systemPrompt: {}",
                            agentSpec.getName(), skill.getName());
                }
            }
        }

        // 展平内联 Skill（includeFile "path"，直接嵌入 AgentSpec）
        // - Logic Skill → 注册为工具
        // - Prompt Skill → 拼接到 systemPrompt（Anthropic Skills 标准用法）
        if (agentSpec.getInlineSkills() != null) {
            for (SkillSpec skill : agentSpec.getInlineSkills()) {
                if (skill.isLogicSkill()) {
                    ToolSpec skillAsTool = flattenSkillToTool(skill);
                    ToolEntry entry = toolBridge.convert(skillAsTool);
                    toolSpecifications.add(entry.specification());
                    toolExecutors.put(entry.specification().name(), entry.executor());
                    log.info("Agent '{}' 注册 inline Logic Skill 为工具: {}",
                            agentSpec.getName(), skill.getName());
                } else {
                    // Prompt Skill (来自 includeFile): 注入到 system prompt
                    skillPromptAppend.append("\n\n---\n### Skill: ").append(skill.getName())
                            .append("\n").append(skill.getInstruction());
                    log.info("Agent '{}' 注入 inline Prompt Skill 到 systemPrompt: {} ({} 字符)",
                            agentSpec.getName(), skill.getName(),
                            skill.getInstruction() != null ? skill.getInstruction().length() : 0);
                }
            }
        }

        // 将 Prompt Skill 内容追加到 systemPrompt
        String finalSystemPrompt = agentSpec.getSystemPrompt() != null
                ? agentSpec.getSystemPrompt()
                : "";
        if (!skillPromptAppend.isEmpty()) {
            finalSystemPrompt = finalSystemPrompt + skillPromptAppend;
            log.info("Agent '{}' systemPrompt 已追加 Skill 知识，总长度: {} 字符",
                    agentSpec.getName(), finalSystemPrompt.length());
        }
        // 将增强后的 systemPrompt 回写到 agentSpec（AgentExecutor 从 spec 读取）
        agentSpec.setSystemPrompt(finalSystemPrompt);

        // 4. 创建 RAG ContentRetriever（如果配置了 RAG）
        ContentRetriever contentRetriever = null;
        if (agentSpec.getRag() != null) {
            contentRetriever = ragFactory.create(agentSpec.getRag());
            log.info("Agent '{}' 已配置 RAG", agentSpec.getName());
        }

        // 5. 处理 MCP 工具
        if (agentSpec.getMcp() != null && !agentSpec.getMcp().getServers().isEmpty()) {
            try {
                List<String> hitlActions = agentSpec.getBrowserUse() != null
                        ? agentSpec.getBrowserUse().getHitlActions()
                        : null;
                McpToolsResult result = mcpBridge.connect(agentSpec.getMcp(), hitlActions);
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

        // 6. 处理 Browser Use (NativeBrowserTool)
        if (agentSpec.getBrowserUse() != null) {
            log.info("Agent '{}' 启用了 Browser Use。初始化 NativeBrowserTool...", agentSpec.getName());
            boolean headless = !agentSpec.getBrowserUse().isSandbox(); // 简单的沙箱映射 (可改)
            NativeBrowserTool browserTool = new NativeBrowserTool(headless);
            activeBrowserTools.add(browserTool);

            List<String> hitlActions = agentSpec.getBrowserUse().getHitlActions();
            SafetyGuard safetyGuard = new SafetyGuard();

            List<ToolSpec> bTools = ToolScanner.scan(browserTool);
            for (ToolSpec ts : bTools) {
                ToolEntry entry = toolBridge.convert(ts);
                String toolName = entry.specification().name();
                ToolExecutor originalExecutor = entry.executor();

                ToolExecutor wrappedExecutor = (toolExecutionRequest, memoryId) -> {
                    if (hitlActions != null && hitlActions.contains(toolName)) {
                        boolean confirmed = safetyGuard.confirmAction(toolName, toolExecutionRequest.arguments());
                        if (!confirmed) {
                            return "Error: Action cancelled by user during HITL confirmation.";
                        }
                    }
                    return originalExecutor.execute(toolExecutionRequest, memoryId);
                };

                toolSpecifications.add(entry.specification());
                toolExecutors.put(toolName, wrappedExecutor);
            }
            log.info("Agent '{}' 加载了 {} 个 NativeBrowserTool 方法",
                    agentSpec.getName(), bTools.size());
        }

        // 7. 组装实例
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
     * 获取所有已注册的 Skill 名称。
     */
    public Set<String> getSkillNames() {
        return Collections.unmodifiableSet(globalSkills.keySet());
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
        globalSkills.clear();
        workflows.clear();
        log.info("注册中心已清空");
    }

    /**
     * 将 SkillSpec 展平为 ToolSpec。
     * <ul>
     * <li>Logic Skill → 直接复用其 executeBody 作为 ToolSpec 的执行闭包。</li>
     * <li>Prompt Skill → 创建一个 Groovy Closure，内部将 instruction 加入到当前 Agent 的
     * System Prompt 上下文中发起子 LLM 调用，返回结果。</li>
     * </ul>
     * <p>
     * 内部使用 Groovy的 GroovyShell 准动执行闭包，确保可序列化且类型安全。
     */
    private ToolSpec flattenSkillToTool(SkillSpec skill) {
        ToolSpec toolSpec = new ToolSpec(skill.getName());
        toolSpec.setDescription(skill.getDescription());
        toolSpec.setParameters(skill.getParameters());

        if (skill.isLogicSkill()) {
            // Logic Skill: 直接复用 executeBody
            toolSpec.setExecuteBody(skill.getExecuteBody());
        } else {
            // Prompt Skill: 生成一个 Groovy Closure，内部将 instruction 注入到 LLM 调用中
            // 此 Closure 接受 params Map，返回 String 结果。
            // 注：此处用一个存储 instruction 的 Closure，实际子 LLM 调用在
            // LangChainToolBridge 执行时处理（通过检测 executeBody 内的标记）。
            // 目前实现为图实调用：将 instruction 和 params 拼接后返回，由主 Agent 的 LLM 分析。
            final String instruction = skill.getInstruction();
            groovy.lang.Closure<?> promptClosure = new groovy.lang.Closure<String>(this) {
                @Override
                public String call(Object... args) {
                    // args[0] 是 params Map
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> params = args.length > 0
                            ? (java.util.Map<String, Object>) args[0]
                            : java.util.Collections.emptyMap();
                    // 构建提示词:将参数注入 instruction 模板
                    StringBuilder prompt = new StringBuilder(instruction);
                    if (!params.isEmpty()) {
                        prompt.append("\n\n输入参数:\n");
                        params.forEach((k, v) -> prompt.append("- ").append(k).append(": ").append(v).append("\n"));
                    }
                    // 返回提示词，让主 Agent 的 LLM 执行后续弄理
                    return prompt.toString();
                }
            };
            toolSpec.setExecuteBody(promptClosure);
        }

        return toolSpec;
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

    /**
     * 关闭分配给各 Agent 的原生浏览器工具进程资源
     */
    public void closeNativeBrowsers() {
        for (NativeBrowserTool tool : activeBrowserTools) {
            try {
                tool.close();
            } catch (Exception e) {
                log.warn("释放 NativeBrowserTool 资源异常: {}", e.getMessage());
            }
        }
        activeBrowserTools.clear();
        log.info("所有 NativeBrowserTool(Playwright) 资源已释放");
    }
}
