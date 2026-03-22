package com.agentdsl.runtime;

import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.McpServerSpec;
import com.agentdsl.core.spec.McpSpec;
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
import com.agentdsl.runtime.skill.BuiltinSkillResolver;
import com.agentdsl.runtime.skill.MemoryBuiltinSkillResolver;
import com.agentdsl.runtime.skill.MemoryCapabilityBridge;
import com.agentdsl.runtime.skill.SkillRegistrationPipeline;
import com.agentdsl.tools.ToolScanner;
import com.agentdsl.tools.builtin.NativeBrowserTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final McpDiscoveryService mcpDiscoveryService;
    private final SkillRegistrationPipeline skillRegistrationPipeline;
    private final List<McpToolsResult> mcpConnections = new ArrayList<>();
    private final List<NativeBrowserTool> activeBrowserTools = new ArrayList<>();

    public AgentRegistry() {
        this(new LangChainModelFactory(), new LangChainMemoryFactory(),
                new LangChainToolBridge(), new LangChainRagFactory(), new SimpleMcpDiscoveryService());
    }

    public AgentRegistry(LangChainModelFactory modelFactory,
            LangChainMemoryFactory memoryFactory,
            LangChainToolBridge toolBridge,
            LangChainRagFactory ragFactory) {
        this(modelFactory, memoryFactory, toolBridge, ragFactory, new SimpleMcpDiscoveryService());
    }

    public AgentRegistry(LangChainModelFactory modelFactory,
            LangChainMemoryFactory memoryFactory,
            LangChainToolBridge toolBridge,
            LangChainRagFactory ragFactory,
            McpDiscoveryService mcpDiscoveryService) {
        this.modelFactory = modelFactory;
        this.memoryFactory = memoryFactory;
        this.toolBridge = toolBridge;
        this.ragFactory = ragFactory;
        this.mcpBridge = new McpToolProviderBridge();
        this.mcpDiscoveryService = mcpDiscoveryService;
        MemoryCapabilityBridge memoryCapabilityBridge = new MemoryCapabilityBridge(toolBridge);
        List<BuiltinSkillResolver> builtinSkillResolvers = List.of(new MemoryBuiltinSkillResolver(memoryCapabilityBridge));
        this.skillRegistrationPipeline = new SkillRegistrationPipeline(globalSkills, builtinSkillResolvers, toolBridge);
        this.memoryFactory.bindModelFactory(modelFactory);

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
        // 若 DSL 中未显式设置 memoryId，将 agent 名称注入为默认值，
        // 确保同名 agent 重启后能使用相同的 memoryId 检索持久化记忆。
        if (agentSpec.getMemory() != null && agentSpec.getMemory().getOption("memoryId") == null) {
            agentSpec.getMemory().putOption("memoryId", agentSpec.getName());
        }
        ChatMemory memory = memoryFactory.create(agentSpec.getMemory(), agentSpec.getModel(), model);

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

        String skillPromptAppend = skillRegistrationPipeline.register(
                agentSpec, memory, toolSpecifications, toolExecutors);

        // 将 Prompt Skill 内容追加到 systemPrompt
        String finalSystemPrompt = agentSpec.getSystemPrompt() != null
                ? agentSpec.getSystemPrompt()
                : "";
        if (!skillPromptAppend.isBlank()) {
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

        // 5. 处理 MCP 工具（静态配置的 npx 命令也做 shebang 包装，与动态发现一致）
        if (agentSpec.getMcp() != null && !agentSpec.getMcp().getServers().isEmpty()) {
            try {
                for (McpServerSpec serverSpec : agentSpec.getMcp().getServers()) {
                    if (serverSpec.getCommand() != null && !serverSpec.getCommand().isEmpty()) {
                        List<String> wrapped = wrapNpxCommandWithNode(serverSpec.getCommand());
                        if (wrapped != serverSpec.getCommand()) {
                            serverSpec.setCommand(wrapped);
                        }
                    }
                }
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
     * 在运行时自动发现并挂载 MCP 工具。
     * V1 仅做最小闭环：发现 → 白名单检查 → 连接 → 注册工具。
     */
    public synchronized boolean tryAutoDiscoverAndAttachTool(AgentInstance instance,
            String missingToolName, String userMessage) {
        if (instance == null || instance.getSpec() == null || !instance.getSpec().isAutoDiscoverMcp()) {
            return false;
        }
        if (instance.getToolExecutors().containsKey(missingToolName)) {
            return true;
        }

        String registryName = instance.getSpec().getMcpRegistry();
        if (registryName == null || registryName.isBlank()) {
            registryName = "mcp.so";
        }

        List<McpDiscoveryService.DiscoveredMcpServer> candidates = mcpDiscoveryService.discover(
                registryName, missingToolName, userMessage);
        if (candidates.isEmpty()) {
            log.warn("[MCP-AUDIT] auto discover failed, agent={}, tool={}, reason=no-candidate",
                    instance.getName(), missingToolName);
            return false;
        }

        log.info("[MCP-AUDIT] will try {} candidate(s), timeout=60s each (tip: -Dagentdsl.mcp.wrapper.debug=true for wrapper log)",
                candidates.size());

        // 依次尝试每个候选，跳过白名单不允许或连接失败的（如超时、缺 API Key 等）
        for (McpDiscoveryService.DiscoveredMcpServer candidate : candidates) {
            List<String> command = candidate.command();
            if (!isAllowedDynamicCommand(command)) {
                log.warn("[MCP-AUDIT] auto discover blocked by whitelist, agent={}, tool={}, command={}",
                        instance.getName(), missingToolName, command);
                continue;
            }
            // 避免“执行环境误判”：部分 npm 包的 bin 是 .js 但无 shebang，被 shell 执行会报 import: command not found
            command = wrapNpxCommandWithNode(command);

            McpServerSpec serverSpec = new McpServerSpec(candidate.serverName());
            serverSpec.setTransport("stdio");
            serverSpec.setCommand(command);
            serverSpec.setTimeout(60);  // 动态发现首次 npx 安装可能较慢，给足时间
            serverSpec.setLogEvents(false);
            if (Boolean.getBoolean("agentdsl.mcp.wrapper.debug")) {
                Map<String, String> env = serverSpec.getEnv() != null ? new HashMap<>(serverSpec.getEnv()) : new HashMap<>();
                env.put("AGENTDSL_MCP_WRAPPER_DEBUG", "1");
                serverSpec.setEnv(env);
                log.info("[MCP-AUDIT] wrapper debug enabled, logs: /tmp/agentdsl-mcp-wrapper.log");
            }

            McpSpec mcpSpec = new McpSpec();
            mcpSpec.setServers(List.of(serverSpec));

            long connectStartMs = System.currentTimeMillis();
            String pkgLabel = command.size() >= 3 ? command.get(command.size() - 1) : candidate.serverName();
            try {
                log.info("[MCP-AUDIT] connecting candidate #{}, pkg={}, agent={}",
                        candidates.indexOf(candidate) + 1, pkgLabel, instance.getName());
                McpToolsResult result = mcpBridge.connect(mcpSpec, null);
                long elapsedMs = System.currentTimeMillis() - connectStartMs;
                if (result.toolSpecifications().isEmpty()) {
                    result.close();
                    log.warn("[MCP-AUDIT] candidate connected but no tools after {}ms, skipping, pkg={}",
                            elapsedMs, pkgLabel);
                    continue;
                }
                mcpConnections.add(result);
                instance.getToolSpecifications().addAll(result.toolSpecifications());
                instance.getToolExecutors().putAll(result.toolExecutors());

                boolean success = instance.getToolExecutors().containsKey(missingToolName);
                log.info("[MCP-AUDIT] auto discover OK after {}ms, server={}, mountedTools={}, success={}",
                        elapsedMs, candidate.serverName(), result.toolSpecifications().size(), success);
                return success || !result.toolSpecifications().isEmpty();
            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - connectStartMs;
                boolean isTimeout = e.getMessage() != null && e.getMessage().contains("Timeout");
                log.warn("[MCP-AUDIT] candidate failed after {}ms, pkg={}, error={}{}",
                        elapsedMs, pkgLabel, e.getMessage(),
                        isTimeout ? " (tip: first npx install can be slow, or package may need API key)" : "");
                // 继续尝试下一个候选
            }
        }

        log.error("[MCP-AUDIT] all {} candidates failed, agent={}, tool={}",
                candidates.size(), instance.getName(), missingToolName);
        return false;
    }

    /**
     * 获取所有已注册的工作流名称。
     */
    public Set<String> getWorkflowNames() {
        return Collections.unmodifiableSet(workflows.keySet());
    }

    /**
     * 将 npx -y @pkg 包装为 node &lt;wrapper&gt; @pkg，确保包的 bin 始终用 Node 执行，
     * 避免无 shebang 的 .js 被 shell 执行导致 "import: command not found"。
     */
    private List<String> wrapNpxCommandWithNode(List<String> command) {
        if (command == null || command.size() < 3) return command;
        if (!"npx".equals(command.get(0)) || !"-y".equals(command.get(1))) return command;
        String pkg = command.get(2);
        if (pkg == null || !pkg.startsWith("@")) return command;

        try {
            Path wrapperPath = getOrExtractNpxWrapperScript();
            List<String> wrapped = new ArrayList<>();
            wrapped.add("node");
            wrapped.add(wrapperPath.toAbsolutePath().toString());
            wrapped.add(pkg);
            if (command.size() > 3) {
                wrapped.addAll(command.subList(3, command.size()));
            }
            log.debug("[MCP-AUDIT] wrapped npx command with node launcher: {} -> {}", command, wrapped);
            return wrapped;
        } catch (Exception e) {
            log.warn("[MCP-AUDIT] could not use npx wrapper, using original command: {}", e.getMessage());
            return command;
        }
    }

    private static volatile Path npxWrapperScriptPath;

    private static Path getOrExtractNpxWrapperScript() throws IOException {
        if (npxWrapperScriptPath != null) return npxWrapperScriptPath;
        synchronized (AgentRegistry.class) {
            if (npxWrapperScriptPath != null) return npxWrapperScriptPath;
            Path temp = Files.createTempFile("agentdsl-mcp-npx-run", ".js");
            temp.toFile().deleteOnExit();
            try (InputStream in = AgentRegistry.class.getResourceAsStream("/mcp-npx-run.js")) {
                if (in == null) throw new IOException("Resource /mcp-npx-run.js not found");
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            npxWrapperScriptPath = temp;
            return temp;
        }
    }

    /**
     * 动态 MCP 命令白名单校验。
     * V1 允许 npx -y @scope/package 格式（scoped npm 包），
     * 以及 docker run 格式。
     */
    private boolean isAllowedDynamicCommand(List<String> command) {
        if (command == null || command.size() < 3) {
            return false;
        }
        String executable = command.get(0);

        // npx -y @scope/package（scoped npm 包）
        if ("npx".equals(executable) && "-y".equals(command.get(1))
                && command.get(2).startsWith("@")) {
            return true;
        }

        // docker run -i --rm ...（容器化 MCP Server）
        if ("docker".equals(executable) && command.contains("run")) {
            return true;
        }

        return false;
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

    // --- 直接执行方法（供 WorkflowExecutor 的非 Agent 步骤使用）---

    /**
     * 直接执行已注册的工具（绕过 LLM）。
     *
     * @param toolName 工具名称
     * @param params   工具参数
     * @return 执行结果字符串
     */
    public String executeToolDirectly(String toolName, Map<String, Object> params) {
        ToolSpec tool = globalTools.get(toolName);
        if (tool == null) {
            throw new DslRuntimeException("ADSL-040",
                    "直接调用工具失败：工具 '" + toolName + "' 未注册。已注册工具: " + globalTools.keySet());
        }
        try {
            if (tool.isBeanMethod()) {
                java.lang.reflect.Method method = tool.getToolBeanMethod();
                Object bean = tool.getToolBean();
                java.lang.reflect.Parameter[] methodParams = method.getParameters();
                List<com.agentdsl.core.spec.ParameterSpec> paramSpecs = tool.getParameters();

                Object[] args = new Object[methodParams.length];
                for (int i = 0; i < methodParams.length; i++) {
                    String paramName = (paramSpecs != null && i < paramSpecs.size())
                            ? paramSpecs.get(i).getName()
                            : methodParams[i].getName();
                    Object val = params.get(paramName);
                    args[i] = com.agentdsl.core.utils.ConvertUtils.convertArg(val, methodParams[i].getType());
                }
                Object result = method.invoke(bean, args);
                return result != null ? result.toString() : "null";
            }
            Object body = tool.getExecuteBody();
            if (body instanceof groovy.lang.Closure<?> closure) {
                Object result = closure.getMaximumNumberOfParameters() == 0
                        ? closure.call()
                        : closure.call(params);
                return result != null ? result.toString() : "null";
            }
            throw new DslRuntimeException("ADSL-040",
                    "工具 '" + toolName + "' 没有可执行逻辑。");
        } catch (DslRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("直接调用工具 '{}' 执行失败", toolName, e);
            throw new DslRuntimeException("ADSL-040",
                    "直接调用工具 '" + toolName + "' 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 直接执行已注册的 Skill（绕过 LLM）。
     *
     * @param skillName Skill 名称
     * @param params    Skill 参数
     * @return 执行结果字符串
     */
    public String executeSkillDirectly(String skillName, Map<String, Object> params) {
        SkillSpec skill = globalSkills.get(skillName);
        if (skill == null) {
            throw new DslRuntimeException("ADSL-040",
                    "直接调用 Skill 失败：Skill '" + skillName + "' 未注册。已注册 Skill: " + globalSkills.keySet());
        }
        try {
            if (skill.isLogicSkill()) {
                Object body = skill.getExecuteBody();
                if (body instanceof groovy.lang.Closure<?> closure) {
                    Object result = closure.getMaximumNumberOfParameters() == 0
                            ? closure.call()
                            : closure.call(params);
                    return result != null ? result.toString() : "null";
                }
                throw new DslRuntimeException("ADSL-040",
                        "Logic Skill '" + skillName + "' 没有可执行逻辑。");
            } else {
                // Prompt Skill: 直接返回 instruction 与参数拼接的结果
                StringBuilder prompt = new StringBuilder(
                        skill.getInstruction() != null ? skill.getInstruction() : "");
                if (params != null && !params.isEmpty()) {
                    prompt.append("\n\n输入参数:\n");
                    params.forEach((k, v) -> prompt.append("- ").append(k).append(": ").append(v).append("\n"));
                }
                return prompt.toString();
            }
        } catch (DslRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("直接调用 Skill '{}' 执行失败", skillName, e);
            throw new DslRuntimeException("ADSL-040",
                    "直接调用 Skill '" + skillName + "' 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取工具调度解析器（供 WorkflowExecutor 注入到 WorkflowExecutionContext）。
     */
    public java.util.function.BiFunction<String, Map<String, Object>, String> getToolCallResolver() {
        return this::executeToolDirectly;
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
