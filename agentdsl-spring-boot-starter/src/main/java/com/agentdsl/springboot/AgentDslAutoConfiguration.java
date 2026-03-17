package com.agentdsl.springboot;

import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.runtime.AgentDslEngine;
import com.agentdsl.runtime.AgentRegistry;
import com.agentdsl.springboot.web.AgentDslRestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AgentDSL Spring Boot 自动配置类。
 * 
 * <p>自动配置 AgentDslEngine Bean，并可选地配置 REST 端点。
 * 通过 agentdsl.* 配置属性进行定制。
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentDslProperties.class)
@ConditionalOnProperty(prefix = "agentdsl", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentDslAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentDslAutoConfiguration.class);

    private final AgentDslProperties properties;

    public AgentDslAutoConfiguration(AgentDslProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建 DslCompiler Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public DslCompiler dslCompiler() {
        boolean sandbox = properties.isSandbox();
        log.info("初始化 DslCompiler (sandbox={})", sandbox);
        return new DslCompiler(sandbox);
    }

    /**
     * 创建 AgentRegistry Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRegistry agentRegistry() {
        log.info("初始化 AgentRegistry");
        return new AgentRegistry();
    }

    /**
     * 创建 AgentDslEngine Bean。
     * 使用测试用构造函数，直接使用 Spring 创建的 AgentRegistry Bean。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public AgentDslEngine agentDslEngine(DslCompiler compiler, AgentRegistry registry) throws IOException {
        boolean sandbox = properties.isSandbox();
        boolean hotReload = properties.isHotReload();
        String scriptsLocation = properties.getScriptsLocation();

        log.info("初始化 AgentDslEngine (sandbox={}, hotReload={}, scriptsLocation={})",
                sandbox, hotReload, scriptsLocation);

        // 使用测试用构造函数，直接使用 Spring 创建的 AgentRegistry Bean
        AgentDslEngine engine = new AgentDslEngine(compiler, registry);

        // 扫描并加载 DSL 脚本（使用 Spring 的 registry，应用模型默认值）
        AgentScanner scanner = new AgentScanner(compiler, properties.getModel());
        AgentScanner.ScanResult scanResult = scanner.scan(scriptsLocation, registry);
        
        log.info("DSL 脚本扫描完成: {}", scanResult);

        // 注册内置工具（通过引擎内部方法）
        // 注意：内置工具已在 AgentDslEngine 构造函数中注册

        // 可选：启动热加载
        if (hotReload) {
            Path watchPath = resolveWatchPath(scriptsLocation);
            if (watchPath != null) {
                engine.watchDirectory(watchPath);
                log.info("已启动热加载监听: {}", watchPath);
            }
        }

        return engine;
    }

    /**
     * 创建 REST Controller（仅在 Web 应用且配置启用时）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "agentdsl.api", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public AgentDslRestController agentDslRestController(AgentDslEngine engine) {
        String basePath = properties.getApi().getBasePath();
        log.info("初始化 AgentDslRestController (basePath={})", basePath);
        return new AgentDslRestController(engine, basePath);
    }

    /**
     * 解析脚本监听路径。
     */
    private Path resolveWatchPath(String scriptsLocation) {
        if (scriptsLocation.startsWith("classpath:")) {
            // 类路径不支持热加载（需要文件系统路径）
            log.warn("热加载不支持 classpath: 路径，请使用 file: 或绝对路径");
            return null;
        }
        
        if (scriptsLocation.startsWith("file:")) {
            return Paths.get(scriptsLocation.substring("file:".length()));
        }
        
       return Paths.get(scriptsLocation);
    }
}