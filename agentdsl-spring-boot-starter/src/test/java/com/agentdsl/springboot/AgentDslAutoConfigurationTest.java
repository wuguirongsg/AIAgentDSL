package com.agentdsl.springboot;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentDslAutoConfiguration 单元测试。
 * 注意：完整的 Spring Boot 集成测试需要独立的 Spring Boot 测试环境。
 */
class AgentDslAutoConfigurationTest {

    @Test
    void shouldCreatePropertiesWithDefaults() {
        AgentDslProperties properties = new AgentDslProperties();
        
        // 验证默认值
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getScriptsLocation()).isEqualTo("classpath:agents/");
        assertThat(properties.isSandbox()).isFalse();
        assertThat(properties.isHotReload()).isFalse();
    }

    @Test
    void shouldAllowDisabling() {
        AgentDslProperties properties = new AgentDslProperties();
        properties.setEnabled(false);
        
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void shouldConfigureModelDefaults() {
        AgentDslProperties properties = new AgentDslProperties();
        
        // 验证模型默认值
        assertThat(properties.getModel().getProvider()).isEqualTo("gemini");
        assertThat(properties.getModel().getModelName()).isEqualTo("gemini-2.0-flash");
        assertThat(properties.getModel().getTemperature()).isEqualTo(0.7);
        assertThat(properties.getModel().getMaxTokens()).isEqualTo(4096);
    }

    @Test
    void shouldConfigureApiDefaults() {
        AgentDslProperties properties = new AgentDslProperties();
        
        // 验证 API 默认值
        assertThat(properties.getApi().isEnabled()).isTrue();
        assertThat(properties.getApi().getBasePath()).isEqualTo("/api");
    }

    @Test
    void shouldAllowCustomConfiguration() {
        AgentDslProperties properties = new AgentDslProperties();
        
        // 自定义配置
        properties.setSandbox(true);
        properties.setHotReload(true);
        properties.setScriptsLocation("file:/custom/path/");
        properties.getModel().setProvider("openai");
        properties.getModel().setModelName("gpt-4");
        properties.getApi().setBasePath("/api/v2");
        
        // 验证
        assertThat(properties.isSandbox()).isTrue();
        assertThat(properties.isHotReload()).isTrue();
        assertThat(properties.getScriptsLocation()).isEqualTo("file:/custom/path/");
        assertThat(properties.getModel().getProvider()).isEqualTo("openai");
        assertThat(properties.getModel().getModelName()).isEqualTo("gpt-4");
        assertThat(properties.getApi().getBasePath()).isEqualTo("/api/v2");
    }
}