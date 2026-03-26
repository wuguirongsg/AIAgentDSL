package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import com.agentdsl.core.spec.DataSourceSpec;
import com.agentdsl.core.spec.DataSourceRegistry;
import com.agentdsl.core.exception.DslRuntimeException;
import com.agentdsl.runtime.autonomous.AutonomousExecutor;
import com.agentdsl.runtime.autonomous.AutonomousResult;
import com.agentdsl.runtime.autonomous.ConsoleUserInteraction;
import com.agentdsl.runtime.autonomous.UserInteraction;
import com.agentdsl.tools.BuiltinToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AgentDSL 引擎 — 端到端的入口。
 * 编译 DSL 脚本 → 注册 Agent 和工具 → 提供对话接口。
 *
 * <pre>
 * AgentDslEngine engine = new AgentDslEngine();
 * engine.load("agent('hello') { model { provider 'ollama'; modelName 'qwen2.5' } }");
 * String reply = engine.chat("hello", "你好");
 * </pre>
 */
public class AgentDslEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentDslEngine.class);

    private final DslCompiler compiler;
    private final AgentRegistry registry;
    private final AgentExecutor executor;
    private final WorkflowExecutor workflowExecutor;
    private AutonomousExecutor autonomousExecutor;
    private HotReloader hotReloader;

    public AgentDslEngine() {
        this(false);
    }

    /**
     * @param enableSandbox 是否启用安全沙箱
     */
    public AgentDslEngine(boolean enableSandbox) {
        this.compiler = new DslCompiler(enableSandbox);
        this.registry = new AgentRegistry();
        this.executor = new AgentExecutor(registry);
        this.workflowExecutor = new WorkflowExecutor(executor, registry);
        this.autonomousExecutor = new AutonomousExecutor(new ConsoleUserInteraction(), this.registry);
        registerBuiltinTools();
    }

    /**
     * 构造函数，允许注入自定义 Registry。
     */
    public AgentDslEngine(DslCompiler compiler, AgentRegistry registry) {
        this.compiler = compiler;
        this.registry = registry;
        this.executor = new AgentExecutor(registry);
        this.workflowExecutor = new WorkflowExecutor(executor, registry);
        this.autonomousExecutor = new AutonomousExecutor(new ConsoleUserInteraction(), this.registry);
        registerBuiltinTools();
    }

    /**
     * 从 DSL 脚本字符串加载并注册所有 Agent 和工具。
     * 
     * @return 编译结果，包含注册的实体和诊断信息
     */
    public DslCompileResult load(String dslScript) {
        DslCompileResult result = compiler.compile(dslScript);
        registerAll(result);
        return result;
    }

    /**
     * 从文件加载 DSL 脚本。
     * 
     * @return 编译结果，包含注册的实体和诊断信息
     */
    public DslCompileResult loadFile(Path scriptPath) {
        DslCompileResult result = compiler.compileFile(scriptPath);
        registerAll(result);
        return result;
    }

    /**
     * 启动热加载监听。
     * 监听指定目录下 .agent.groovy 文件的变更，自动重编译并更新注册。
     *
     * @param directory 监听目录
     * @throws IOException 如果 WatchService 创建失败
     */
    public void watchDirectory(Path directory) throws IOException {
        if (hotReloader != null) {
            hotReloader.stop();
        }
        hotReloader = new HotReloader(compiler, registry);
        hotReloader.watch(directory);
    }

    /**
     * 停止热加载监听。
     */
    public void stopWatching() {
        if (hotReloader != null) {
            hotReloader.stop();
            hotReloader = null;
        }
    }

    /**
     * 向指定 Agent 发送消息。
     */
    public String chat(String agentName, String userMessage) {
        return executor.chat(agentName, userMessage);
    }

    /**
     * 以自主模式执行 Agent 任务。
     *
     * @param agentName Agent 名称
     * @param userGoal  用户目标描述
     * @return 自主执行结果
     */
    public AutonomousResult executeAutonomous(String agentName, String userGoal) {
        AgentInstance instance = registry.get(agentName);
        if (!instance.getSpec().isAutonomous()) {
            throw new DslRuntimeException("ADSL-030",
                    "Agent '" + agentName + "' 未配置 autonomous 模式");
        }
        return autonomousExecutor.execute(instance, userGoal);
    }

    /**
     * 设置自定义的用户交互实现（用于测试或 Web 场景）。
     */
    public void setUserInteraction(UserInteraction userInteraction) {
        this.autonomousExecutor = new AutonomousExecutor(userInteraction, this.registry);
    }

    /**
     * 执行指定名称的工作流。
     *
     * @param workflowName 工作流名称
     * @param input        初始输入
     * @return 工作流执行结果
     */
    public WorkflowResult executeWorkflow(String workflowName, String input) {
        return workflowExecutor.execute(workflowName, input);
    }

    /**
     * 获取工作流执行器。
     */
    public WorkflowExecutor getWorkflowExecutor() {
        return workflowExecutor;
    }

    /**
     * 获取注册中心。
     */
    public AgentRegistry getRegistry() {
        return registry;
    }

    /**
     * 获取执行器。
     */
    public AgentExecutor getExecutor() {
        return executor;
    }

    /**
     * 获取自主执行器。
     */
    public AutonomousExecutor getAutonomousExecutor() {
        return autonomousExecutor;
    }

    /**
     * 获取编译器。
     */
    public DslCompiler getCompiler() {
        return compiler;
    }

    @Override
    public void close() {
        registry.closeMcpConnections();
        registry.closeNativeBrowsers();
        stopWatching();
    }

    /**
     * 注册内置工具（HTTP / JSON / File 等）。
     * 在引擎初始化时自动调用，DSL 脚本可通过 include 引用这些工具。
     * <p>
     * FileTool 白名单：/tmp + 当前工作目录（含 output/ 子目录），
     * 让 Agent 可以将生成的文件保存到项目本地目录。
     */
    private void registerBuiltinTools() {
        String cwd = System.getProperty("user.dir");
        List<String> allowedDirs = List.of(
                "/tmp",
                cwd,
                cwd + "/output",
                cwd + "/examples");
        List<ToolSpec> builtinTools = BuiltinToolRegistry.getBuiltinTools(allowedDirs);
        if (!builtinTools.isEmpty()) {
            registry.registerTools(builtinTools);
            // 告知编译器内置工具名称，使校验时不产生误报 Warning
            Set<String> builtinToolNames = builtinTools.stream()
                    .map(ToolSpec::getName)
                    .collect(Collectors.toSet());
            compiler.setKnownBuiltinToolNames(builtinToolNames);
            log.info("注册了 {} 个内置工具（文件白名单: /tmp, {}/output）", builtinTools.size(), cwd);
        }
    }

    /**
     * 注册编译结果中的所有工具和 Agent。
     * 工具先于 Agent 注册，以支持 Agent 通过 include 引用工具。
     * Skill 先于 Agent 注册，以支持 Agent 通过 skills { include } 引用技能。
     */
    private void registerAll(DslCompileResult result) {
        // 先注册独立工具
        List<ToolSpec> tools = result.getTools();
        if (!tools.isEmpty()) {
            registry.registerTools(tools);
            log.info("注册了 {} 个全局工具", tools.size());
        }

        // 注册 Skills（在 Agent 之前）
        List<SkillSpec> skills = result.getSkills();
        if (!skills.isEmpty()) {
            registry.registerSkills(skills);
            log.info("注册了 {} 个全局技能", skills.size());
        }

        // 注册 DataSources
        List<DataSourceSpec> datasources = result.getDatasources();
        for (DataSourceSpec ds : datasources) {
            DataSourceRegistry.register(ds);
        }
        if (!datasources.isEmpty()) {
            log.info("注册了 {} 个数据源", datasources.size());
        }

        // 再注册 Agent
        List<AgentSpec> agents = result.getAgents();
        for (AgentSpec agent : agents) {
            registry.register(agent);
        }
        log.info("注册了 {} 个 Agent", agents.size());

        // 最后注册工作流
        List<WorkflowSpec> workflows = result.getWorkflows();
        if (!workflows.isEmpty()) {
            registry.registerWorkflows(workflows);
            log.info("注册了 {} 个 Workflow", workflows.size());
        }
    }
}
