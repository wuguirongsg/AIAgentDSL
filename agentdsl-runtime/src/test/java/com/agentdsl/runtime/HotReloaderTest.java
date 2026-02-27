package com.agentdsl.runtime;

import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.langchain4j.LangChainMemoryFactory;
import com.agentdsl.langchain4j.LangChainRagFactory;
import com.agentdsl.langchain4j.LangChainToolBridge;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HotReloader 热加载测试。
 */
class HotReloaderTest {

    private Path tempDir;
    private DslCompiler compiler;
    private AgentRegistry registry;
    private HotReloader reloader;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("hotreload-test");
        compiler = new DslCompiler(false);
        registry = new AgentRegistry(
                new StubModelFactory(new StubChatModel().addTextResponse("test")),
                new LangChainMemoryFactory(),
                new LangChainToolBridge(),
                new LangChainRagFactory());
        reloader = new HotReloader(compiler, registry);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (reloader != null) {
            reloader.stop();
        }
        // 清理临时文件
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    @DisplayName("启动时加载已有脚本")
    void shouldLoadExistingScripts() throws IOException {
        // 先写入一个脚本文件
        Path scriptFile = tempDir.resolve("test.agent.groovy");
        Files.writeString(scriptFile, """
                    agent("hot-agent") {
                        model {
                            provider "ollama"
                            modelName "qwen:0.5b-chat"
                        }
                        systemPrompt "你好"
                    }
                """);

        // 启动 HotReloader — 应加载已有文件
        reloader.watch(tempDir);

        assertTrue(reloader.isRunning());
        assertTrue(registry.has("hot-agent"), "应加载已有脚本中的 Agent");
    }

    @Test
    @DisplayName("文件创建后自动检测并注册 Agent")
    void shouldAutoDetectNewFile() throws Exception {
        // 先启动监听空目录
        reloader.watch(tempDir);
        assertTrue(reloader.isRunning());
        assertFalse(registry.has("new-agent"));

        // 创建新脚本文件
        Path scriptFile = tempDir.resolve("new.agent.groovy");
        Files.writeString(scriptFile, """
                    agent("new-agent") {
                        model {
                            provider "ollama"
                            modelName "qwen:0.5b-chat"
                        }
                        systemPrompt "auto loaded"
                    }
                """);

        // 等待 WatchService 检测和处理
        Thread.sleep(2000);

        assertTrue(registry.has("new-agent"), "应自动检测新文件并注册 Agent");
    }

    @Test
    @DisplayName("直接调用 reloadFile 可重新编译注册")
    void shouldReloadFileDirectly() throws IOException {
        Path scriptFile = tempDir.resolve("reload.agent.groovy");
        Files.writeString(scriptFile, """
                    agent("reload-agent") {
                        model {
                            provider "ollama"
                            modelName "qwen:0.5b-chat"
                        }
                        systemPrompt "v1"
                    }
                """);

        reloader.reloadFile(scriptFile);
        assertTrue(registry.has("reload-agent"));
    }

    @Test
    @DisplayName("stop 后不再运行")
    void shouldStopCleanly() throws IOException {
        reloader.watch(tempDir);
        assertTrue(reloader.isRunning());

        reloader.stop();
        assertFalse(reloader.isRunning());
    }
}
