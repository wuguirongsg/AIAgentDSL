package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DSL 脚本热加载器。
 * 基于 {@link WatchService} 监听指定目录下 .agent.groovy 文件的变更，
 * 自动重编译并更新 AgentRegistry。
 *
 * <pre>
 * HotReloader reloader = new HotReloader(compiler, registry);
 * reloader.watch(Path.of("scripts/"));
 * // ... 修改脚本文件，Agent 自动更新 ...
 * reloader.stop();
 * </pre>
 */
public class HotReloader implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HotReloader.class);
    private static final String DSL_EXTENSION = ".agent.groovy";
    private static final long DEBOUNCE_MS = 500;

    private final DslCompiler compiler;
    private final AgentRegistry registry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread watchThread;
    private WatchService watchService;

    /** 记录每个文件注册的 Agent 名称，用于删除时注销 */
    private final ConcurrentMap<String, List<String>> fileAgentMap = new ConcurrentHashMap<>();

    public HotReloader(DslCompiler compiler, AgentRegistry registry) {
        this.compiler = compiler;
        this.registry = registry;
    }

    /**
     * 开始监听指定目录。非阻塞，在后台线程运行。
     *
     * @param directory 监听的目录路径
     * @throws IOException 如果 WatchService 创建失败
     */
    public void watch(Path directory) throws IOException {
        if (running.get()) {
            throw new IllegalStateException("HotReloader 已在运行中");
        }

        // 先加载目录中已有的脚本
        loadExisting(directory);

        // 启动 WatchService
        watchService = FileSystems.getDefault().newWatchService();
        directory.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

        running.set(true);

        watchThread = new Thread(() -> pollLoop(directory), "dsl-hot-reloader");
        watchThread.setDaemon(true);
        watchThread.start();

        log.info("HotReloader 启动，监听目录: {}", directory);
    }

    /**
     * 停止监听。
     */
    public void stop() {
        running.set(false);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("关闭 WatchService 异常", e);
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
        log.info("HotReloader 已停止");
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * 加载目录中已存在的 DSL 脚本。
     */
    private void loadExisting(Path directory) {
        try (var stream = Files.list(directory)) {
            stream.filter(p -> p.toString().endsWith(DSL_EXTENSION))
                    .forEach(this::reloadFile);
        } catch (IOException e) {
            log.warn("加载已有脚本失败: {}", directory, e);
        }
    }

    /**
     * 文件监听主循环。
     */
    private void pollLoop(Path directory) {
        // 防抖：记录上次事件时间
        ConcurrentMap<String, Long> lastEventTime = new ConcurrentHashMap<>();

        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW)
                        continue;

                    @SuppressWarnings("unchecked")
                    Path filename = ((WatchEvent<Path>) event).context();
                    String name = filename.toString();

                    if (!name.endsWith(DSL_EXTENSION))
                        continue;

                    Path fullPath = directory.resolve(filename);

                    // 防抖检查
                    long now = System.currentTimeMillis();
                    Long lastTime = lastEventTime.get(name);
                    if (lastTime != null && (now - lastTime) < DEBOUNCE_MS) {
                        continue;
                    }
                    lastEventTime.put(name, now);

                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        handleDelete(name);
                    } else {
                        // CREATE 或 MODIFY
                        // 短暂延迟以等待文件写入完成
                        Thread.sleep(100);
                        reloadFile(fullPath);
                    }
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception e) {
                log.error("HotReloader 处理事件异常", e);
            }
        }
    }

    /**
     * 重新编译并注册脚本。
     */
    void reloadFile(Path scriptPath) {
        String fileName = scriptPath.getFileName().toString();
        try {
            log.info("热加载脚本: {}", scriptPath);

            DslCompileResult result = compiler.compileFile(scriptPath);

            // 先注销旧的 Agent
            List<String> oldAgents = fileAgentMap.get(fileName);
            if (oldAgents != null) {
                for (String agentName : oldAgents) {
                    registry.unregister(agentName);
                }
            }

            // 注册工具
            List<ToolSpec> tools = result.getTools();
            if (!tools.isEmpty()) {
                registry.registerTools(tools);
            }

            // 注册 Agent 并记录
            List<String> newAgentNames = result.getAgents().stream()
                    .map(AgentSpec::getName)
                    .toList();

            for (AgentSpec agent : result.getAgents()) {
                registry.register(agent);
            }
            fileAgentMap.put(fileName, newAgentNames);

            log.info("热加载完成: {} agents, {} tools from {}",
                    result.getAgents().size(), tools.size(), fileName);
        } catch (Exception e) {
            log.error("热加载脚本失败: {}", scriptPath, e);
        }
    }

    /**
     * 处理脚本文件被删除。
     */
    private void handleDelete(String fileName) {
        List<String> agentNames = fileAgentMap.remove(fileName);
        if (agentNames != null) {
            for (String name : agentNames) {
                registry.unregister(name);
                log.info("脚本删除，注销 Agent: {}", name);
            }
        }
    }

    /**
     * 是否正在运行。
     */
    public boolean isRunning() {
        return running.get();
    }
}
