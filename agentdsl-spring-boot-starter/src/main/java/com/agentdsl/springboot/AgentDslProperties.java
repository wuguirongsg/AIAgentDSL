package com.agentdsl.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentDSL Spring Boot 配置属性。
 * 
 * <p>通过 application.yml 中的 agentdsl.* 前缀进行配置：
 * 
 * <pre>
 * agentdsl:
 *   enabled: true
 *   scripts-location: classpath:agents/
 *   sandbox: false
 *   hot-reload: false
 *   model:
 *     provider: gemini
 *     model-name: gemini-2.0-flash
 *     temperature: 0.7
 *     max-tokens: 4096
 *   api:
 *     enabled: true
 *     base-path: /api
 * </pre>
 * 
 * @see AgentDslAutoConfiguration
 */
@ConfigurationProperties(prefix = "agentdsl")
public class AgentDslProperties {

    /**
     * 是否启用 AgentDSL 自动配置。
     * 默认为 true，设置为 false 可完全禁用 Starter。
     */
    private boolean enabled = true;

    /**
     * DSL 脚本扫描目录。
     * 支持 classpath: 和 file: 前缀。
     * 默认为 classpath:agents/
     */
    private String scriptsLocation = "classpath:agents/";

    /**
     * 是否启用安全沙箱模式。
     * 开启后，DSL 脚本中的危险操作将被限制。
     */
    private boolean sandbox = false;

    /**
     * 是否启用热加载。
     * 开启后，脚本文件变更会自动重新加载。
     * 建议仅在开发环境启用。
     */
    private boolean hotReload = false;

    /**
     * 全局模型配置默认值。
     * 当 Agent DSL 脚本未指定模型时使用这些默认值。
     */
    private ModelDefaults model = new ModelDefaults();

    /**
     * REST API 配置。
     */
    private ApiConfig api = new ApiConfig();

    // --- Getters and Setters ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getScriptsLocation() {
        return scriptsLocation;
    }

    public void setScriptsLocation(String scriptsLocation) {
        this.scriptsLocation = scriptsLocation;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public boolean isHotReload() {
        return hotReload;
    }

    public void setHotReload(boolean hotReload) {
        this.hotReload = hotReload;
    }

    public ModelDefaults getModel() {
        return model;
    }

    public void setModel(ModelDefaults model) {
        this.model = model;
    }

    public ApiConfig getApi() {
        return api;
    }

    public void setApi(ApiConfig api) {
        this.api = api;
    }

    // --- Inner Classes ---

    /**
     * 全局模型默认配置。
     */
    public static class ModelDefaults {

        /**
         * 默认模型提供商。
         * 支持: openai, ollama, gemini, claude, deepseek, kimi, doubao, qwen, zhipu, minimax
         */
        private String provider = "gemini";

        /**
         * 默认模型名称。
         */
        private String modelName = "gemini-2.0-flash";

        /**
         * 默认温度参数 (0.0 - 2.0)。
         */
        private Double temperature = 0.7;

        /**
         * 默认最大输出 Token 数。
         */
        private Integer maxTokens = 4096;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    /**
     * REST API 配置。
     */
    public static class ApiConfig {

        /**
         * 是否启用 REST API 端点。
         */
        private boolean enabled = true;

        /**
         * REST API 基础路径。
         * 默认为 /api
         */
        private String basePath = "/api";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }
    }
}