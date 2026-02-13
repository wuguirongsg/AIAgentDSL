package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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
    }

    /**
     * 测试用构造函数，允许注入自定义 Registry。
     */
    AgentDslEngine(DslCompiler compiler, AgentRegistry registry) {
        this.compiler = compiler;
        this.registry = registry;
        this.executor = new AgentExecutor(registry);
    }

    /**
     * 从 DSL 脚本字符串加载并注册所有 Agent 和工具。
     */
    public void load(String dslScript) {
        DslCompileResult result = compiler.compile(dslScript);
        registerAll(result);
    }

    /**
     * 从文件加载 DSL 脚本。
     */
    public void loadFile(Path scriptPath) {
        DslCompileResult result = compiler.compileFile(scriptPath);
        registerAll(result);
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
     * 获取编译器。
     */
    public DslCompiler getCompiler() {
        return compiler;
    }

    @Override
    public void close() {
        stopWatching();
    }

    /**
     * 注册编译结果中的所有工具和 Agent。
     * 工具先于 Agent 注册，以支持 Agent 通过 include 引用工具。
     */
    private void registerAll(DslCompileResult result) {
        // 先注册独立工具
        List<ToolSpec> tools = result.getTools();
        if (!tools.isEmpty()) {
            registry.registerTools(tools);
            log.info("注册了 {} 个全局工具", tools.size());
        }

        // 再注册 Agent
        List<AgentSpec> agents = result.getAgents();
        for (AgentSpec agent : agents) {
            registry.register(agent);
        }
        log.info("注册了 {} 个 Agent", agents.size());
    }
}
