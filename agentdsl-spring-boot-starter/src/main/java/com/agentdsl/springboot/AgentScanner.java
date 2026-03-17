package com.agentdsl.springboot;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.DataSourceRegistry;
import com.agentdsl.core.spec.DataSourceSpec;
import com.agentdsl.core.spec.ModelSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import com.agentdsl.runtime.AgentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DSL 脚本扫描器。
 * 
 * <p>负责从指定目录扫描 .agent.groovy 文件并加载到 AgentDslEngine。
 * 支持以下路径前缀：
 * <ul>
 *   <li>classpath: - 从类路径加载</li>
 *   <li>file: - 从文件系统加载</li>
 *   <li>无前缀 - 作为文件路径处理</li>
 * </ul>
 */
public class AgentScanner {

    private static final Logger log = LoggerFactory.getLogger(AgentScanner.class);
    private static final String DSL_EXTENSION = ".agent.groovy";

    private final DslCompiler compiler;
    private final AgentDslProperties.ModelDefaults modelDefaults;

    public AgentScanner(DslCompiler compiler) {
        this(compiler, null);
    }

    public AgentScanner(DslCompiler compiler, AgentDslProperties.ModelDefaults modelDefaults) {
        this.compiler = compiler;
        this.modelDefaults = modelDefaults;
    }

    /**
     * 扫描指定目录下的所有 DSL 脚本并注册到引擎。
     *
     * @param location   脚本位置（支持 classpath: 和 file: 前缀）
     * @param registry   Agent 注册中心
     * @return 扫描结果统计
     */
    public ScanResult scan(String location, AgentRegistry registry) {
        ScanResult result = new ScanResult();
        
        try {
            List<ScriptEntry> scripts = loadScripts(location);
            
            for (ScriptEntry scriptEntry : scripts) {
                try {
                    DslCompileResult compileResult = compiler.compile(scriptEntry.content(), scriptEntry.name());
                    
                    // 注册工具（先于 Agent，以支持 Agent 通过 include 引用）
                    List<ToolSpec> tools = compileResult.getTools();
                    if (!tools.isEmpty()) {
                        registry.registerTools(tools);
                        result.toolsAdded += tools.size();
                    }

                    // 注册 Skills（先于 Agent，以支持 Agent 通过 skills { include } 引用）
                    List<SkillSpec> skills = compileResult.getSkills();
                    if (!skills.isEmpty()) {
                        registry.registerSkills(skills);
                        result.skillsAdded += skills.size();
                    }

                    // 注册 DataSources
                    List<DataSourceSpec> datasources = compileResult.getDatasources();
                    for (DataSourceSpec ds : datasources) {
                        DataSourceRegistry.register(ds);
                    }
                    if (!datasources.isEmpty()) {
                        result.datasourcesAdded += datasources.size();
                    }
                    
                    // 注册 Agent（应用模型默认值）
                    for (AgentSpec agent : compileResult.getAgents()) {
                        applyModelDefaults(agent);
                        registry.register(agent);
                        result.agentsAdded++;
                    }
                    
                    // 注册 Workflow
                    List<WorkflowSpec> workflows = compileResult.getWorkflows();
                    if (!workflows.isEmpty()) {
                        registry.registerWorkflows(workflows);
                        result.workflowsAdded += workflows.size();
                    }
                    
                    result.filesLoaded++;
                    log.info("已加载 DSL 脚本: {} (agents={}, tools={}, skills={}, workflows={})",
                            scriptEntry.name(),
                            compileResult.getAgents().size(),
                            tools.size(),
                            skills.size(),
                            workflows.size());
                            
                } catch (Exception e) {
                    result.errors++;
                    log.error("加载 DSL 脚本失败: {}", scriptEntry.name(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("扫描 DSL 脚本目录失败: {}", location, e);
            result.errors++;
        }
        
        return result;
    }

    /**
     * 为 Agent 应用模型默认值。
     * 当 Agent 的 model 配置不完整时，使用全局默认值填充。
     */
    private void applyModelDefaults(AgentSpec agent) {
        if (modelDefaults == null) {
            return;
        }
        
        ModelSpec model = agent.getModel();
        if (model == null) {
            // 如果 Agent 没有配置 model，创建一个使用默认值的新 ModelSpec
            model = new ModelSpec();
            model.setProvider(modelDefaults.getProvider());
            model.setModelName(modelDefaults.getModelName());
            if (modelDefaults.getTemperature() != null) {
                model.setTemperature(modelDefaults.getTemperature());
            }
            if (modelDefaults.getMaxTokens() != null) {
                model.setMaxTokens(modelDefaults.getMaxTokens());
            }
            agent.setModel(model);
            log.debug("Agent '{}' 使用默认模型配置: provider={}, modelName={}",
                    agent.getName(), model.getProvider(), model.getModelName());
        } else {
            // 如果 Agent 已有 model 配置，仅填充缺失的字段
            boolean modified = false;
            if (model.getProvider() == null || model.getProvider().isBlank()) {
                model.setProvider(modelDefaults.getProvider());
                modified = true;
            }
            if (model.getModelName() == null || model.getModelName().isBlank()) {
                model.setModelName(modelDefaults.getModelName());
                modified = true;
            }
            if (model.getTemperature() == null && modelDefaults.getTemperature() != null) {
                model.setTemperature(modelDefaults.getTemperature());
                modified = true;
            }
            if (model.getMaxTokens() == null && modelDefaults.getMaxTokens() != null) {
                model.setMaxTokens(modelDefaults.getMaxTokens());
                modified = true;
            }
            if (modified) {
                log.debug("Agent '{}' 补充模型默认值: provider={}, modelName={}, temp={}, maxTokens={}",
                        agent.getName(), model.getProvider(), model.getModelName(),
                        model.getTemperature(), model.getMaxTokens());
            }
        }
    }

    /**
     * 加载指定位置的所有 DSL 脚本，返回脚本内容列表。
     * 内部使用 ScriptEntry（名称 + 内容），避免 JAR FileSystem 关闭后 Path 失效的问题。
     */
    private List<ScriptEntry> loadScripts(String location) throws IOException, URISyntaxException {
        if (location.startsWith("classpath:")) {
            return loadClasspathScripts(location.substring("classpath:".length()));
        } else if (location.startsWith("file:")) {
            return loadFileSystemScripts(location.substring("file:".length()));
        } else {
            return loadFileSystemScripts(location);
        }
    }

    /**
     * 解析脚本位置，返回实际的文件路径列表（仅对文件系统路径有效，供外部测试使用）。
     */
    public List<Path> resolveScriptsLocation(String location) throws IOException, URISyntaxException {
        if (location.startsWith("classpath:")) {
            String cl = location.substring("classpath:".length());
            List<Path> paths = new ArrayList<>();
            Enumeration<java.net.URL> resources = getClass().getClassLoader().getResources(cl);
            while (resources.hasMoreElements()) {
                URI uri = resources.nextElement().toURI();
                if ("file".equals(uri.getScheme())) {
                    Path basePath = Paths.get(uri);
                    try (Stream<Path> stream = Files.walk(basePath)) {
                        stream.filter(p -> p.toString().endsWith(DSL_EXTENSION)).forEach(paths::add);
                    }
                }
            }
            return paths;
        } else if (location.startsWith("file:")) {
            return collectFilePaths(Paths.get(location.substring("file:".length())));
        } else {
            return collectFilePaths(Paths.get(location));
        }
    }

    /**
     * 从类路径加载脚本内容。
     * 对文件系统路径直接读取；对 JAR 路径在 FileSystem 打开期间读取内容，
     * 避免 FileSystem 关闭后 Path 对象失效。
     */
    private List<ScriptEntry> loadClasspathScripts(String classpathLocation) throws IOException, URISyntaxException {
        List<ScriptEntry> entries = new ArrayList<>();

        Enumeration<java.net.URL> resources = getClass().getClassLoader().getResources(classpathLocation);
        log.debug("正在扫描类路径目录: {}", classpathLocation);

        while (resources.hasMoreElements()) {
            java.net.URL url = resources.nextElement();
            URI uri = url.toURI();
            log.debug("找到资源 URL: {} (scheme: {})", url, uri.getScheme());

            if ("file".equals(uri.getScheme())) {
                Path basePath = Paths.get(uri);
                log.debug("从文件系统加载: {}", basePath);
                try (Stream<Path> stream = Files.walk(basePath)) {
                    List<Path> matched = stream.filter(p -> p.toString().endsWith(DSL_EXTENSION))
                            .collect(Collectors.toList());
                    for (Path p : matched) {
                        entries.add(new ScriptEntry(p.getFileName().toString(), Files.readString(p)));
                    }
                }
            } else if ("jar".equals(uri.getScheme())) {
                log.debug("从 JAR 包加载: {}", uri);
                entries.addAll(readScriptsFromJar(uri, classpathLocation));
            }
        }

        log.debug("类路径扫描结果: 找到 {} 个脚本", entries.size());
        if (entries.isEmpty()) {
            log.warn("类路径脚本目录不存在或为空: {}", classpathLocation);
        }
        return entries;
    }

    /**
     * 在 JAR FileSystem 打开期间读取脚本内容，返回后 FileSystem 已关闭但内容仍可用。
     */
    private List<ScriptEntry> readScriptsFromJar(URI jarUri, String classpathLocation) {
        List<ScriptEntry> entries = new ArrayList<>();
        String jarPath = jarUri.getSchemeSpecificPart().split("!")[0];
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + jarPath), Map.of())) {
            Path searchPath = fs.getPath("/").resolve(classpathLocation);
            if (Files.exists(searchPath) && Files.isDirectory(searchPath)) {
                try (Stream<Path> stream = Files.walk(searchPath)) {
                    List<Path> matched = stream.filter(p -> p.toString().endsWith(DSL_EXTENSION))
                            .collect(Collectors.toList());
                    for (Path p : matched) {
                        // 在 FileSystem 关闭前读取内容
                        String name = p.getFileName().toString();
                        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                        entries.add(new ScriptEntry(name, content));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 JAR 包读取脚本失败: {}", jarUri, e);
        }
        return entries;
    }

    /**
     * 从文件系统加载脚本内容。
     */
    private List<ScriptEntry> loadFileSystemScripts(String fileLocation) throws IOException {
        List<Path> paths = collectFilePaths(Paths.get(fileLocation));
        List<ScriptEntry> entries = new ArrayList<>(paths.size());
        for (Path p : paths) {
            entries.add(new ScriptEntry(p.getFileName().toString(), Files.readString(p)));
        }
        return entries;
    }

    private List<Path> collectFilePaths(Path basePath) throws IOException {
        if (!Files.exists(basePath)) {
            log.warn("脚本目录不存在: {}", basePath);
            return Collections.emptyList();
        }
        if (Files.isRegularFile(basePath)) {
            return List.of(basePath);
        }
        try (Stream<Path> stream = Files.walk(basePath)) {
            return stream.filter(p -> p.toString().endsWith(DSL_EXTENSION))
                    .collect(Collectors.toList());
        }
    }

    /**
     * DSL 脚本内容载体，解耦于文件系统路径，支持 JAR 内脚本的安全读取。
     */
    private record ScriptEntry(String name, String content) {}

    /**
     * 扫描结果统计。
     */
    public static class ScanResult {
        int filesLoaded = 0;
        int agentsAdded = 0;
        int toolsAdded = 0;
        int skillsAdded = 0;
        int datasourcesAdded = 0;
        int workflowsAdded = 0;
        int errors = 0;

        public int getFilesLoaded() {
            return filesLoaded;
        }

        public int getAgentsAdded() {
            return agentsAdded;
        }

        public int getToolsAdded() {
            return toolsAdded;
        }

        public int getSkillsAdded() {
            return skillsAdded;
        }

        public int getDatasourcesAdded() {
            return datasourcesAdded;
        }

        public int getWorkflowsAdded() {
            return workflowsAdded;
        }

        public int getErrors() {
            return errors;
        }

        @Override
        public String toString() {
            return String.format(
                    "ScanResult{files=%d, agents=%d, tools=%d, skills=%d, datasources=%d, workflows=%d, errors=%d}",
                    filesLoaded, agentsAdded, toolsAdded, skillsAdded, datasourcesAdded, workflowsAdded, errors);
        }
    }
}