package com.agentdsl.springboot;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentDslProperties 配置属性测试。
 */
class AgentDslPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        AgentDslProperties properties = new AgentDslProperties();
        
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getScriptsLocation()).isEqualTo("classpath:agents/");
        assertThat(properties.isSandbox()).isFalse();
        assertThat(properties.isHotReload()).isFalse();
        
        assertThat(properties.getModel().getProvider()).isEqualTo("gemini");
        assertThat(properties.getModel().getModelName()).isEqualTo("gemini-2.0-flash");
        assertThat(properties.getModel().getTemperature()).isEqualTo(0.7);
        assertThat(properties.getModel().getMaxTokens()).isEqualTo(4096);
        
        assertThat(properties.getApi().isEnabled()).isTrue();
        assertThat(properties.getApi().getBasePath()).isEqualTo("/api");
    }

    @Test
    void shouldBeDisableable() {
        AgentDslProperties properties = new AgentDslProperties();
        properties.setEnabled(false);
        
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void shouldAllowCustomScriptsLocation() {
        AgentDslProperties properties = new AgentDslProperties();
        properties.setScriptsLocation("file:/opt/agents/");
        
        assertThat(properties.getScriptsLocation()).isEqualTo("file:/opt/agents/");
    }

    @Test
    void shouldAllowCustomModelDefaults() {
        AgentDslProperties properties = new AgentDslProperties();
        properties.getModel().setProvider("openai");
        properties.getModel().setModelName("gpt-4");
        properties.getModel().setTemperature(0.5);
        properties.getModel().setMaxTokens(8192);
        
        assertThat(properties.getModel().getProvider()).isEqualTo("openai");
        assertThat(properties.getModel().getModelName()).isEqualTo("gpt-4");
        assertThat(properties.getModel().getTemperature()).isEqualTo(0.5);
        assertThat(properties.getModel().getMaxTokens()).isEqualTo(8192);
    }

    @Test
    void shouldAllowCustomApiConfig() {
        AgentDslProperties properties = new AgentDslProperties();
        properties.getApi().setEnabled(false);
        properties.getApi().setBasePath("/api/ai");
        
        assertThat(properties.getApi().isEnabled()).isFalse();
        assertThat(properties.getApi().getBasePath()).isEqualTo("/api/ai");
    }
}