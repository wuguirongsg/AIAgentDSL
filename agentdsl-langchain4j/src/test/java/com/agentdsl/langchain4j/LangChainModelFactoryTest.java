package com.agentdsl.langchain4j;

import com.agentdsl.core.spec.ModelSpec;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LangChainModelFactoryTest {

    @Test
    void testCreateMoonshot() {
        ModelSpec spec = new ModelSpec();
        spec.setProvider("moonshot");
        spec.setModelName("moonshot-v1-8k");
        spec.setApiKey("test-key");
        
        LangChainModelFactory factory = new LangChainModelFactory();
        ChatModel model = factory.create(spec);
        assertNotNull(model);
    }
    
    @Test
    void testCreateDoubao() {
        ModelSpec spec = new ModelSpec();
        spec.setProvider("doubao");
        spec.setModelName("ep-xxxxxx");
        spec.setApiKey("test-key");
        
        LangChainModelFactory factory = new LangChainModelFactory();
        ChatModel model = factory.create(spec);
        assertNotNull(model);
    }

    @Test
    void testCreateQwen() {
        ModelSpec spec = new ModelSpec();
        spec.setProvider("qwen");
        spec.setModelName("qwen-max");
        spec.setApiKey("test-key");
        
        LangChainModelFactory factory = new LangChainModelFactory();
        ChatModel model = factory.create(spec);
        assertNotNull(model);
    }

    @Test
    void testCreateZhipu() {
        ModelSpec spec = new ModelSpec();
        spec.setProvider("zhipu");
        spec.setModelName("glm-4");
        spec.setApiKey("test-key");
        
        LangChainModelFactory factory = new LangChainModelFactory();
        ChatModel model = factory.create(spec);
        assertNotNull(model);
    }

    @Test
    void testCreateMinimax() {
        ModelSpec spec = new ModelSpec();
        spec.setProvider("minimax");
        spec.setModelName("abab6.5-chat");
        spec.setApiKey("test-key");
        
        LangChainModelFactory factory = new LangChainModelFactory();
        ChatModel model = factory.create(spec);
        assertNotNull(model);
    }

    @Test
    void testCreateClaude() {
        ModelSpec spec = new ModelSpec();
        spec.setProvider("claude");
        spec.setModelName("claude-3-opus-20240229");
        spec.setApiKey("test-key");
        
        LangChainModelFactory factory = new LangChainModelFactory();
        ChatModel model = factory.create(spec);
        assertNotNull(model);
    }

    @Test
    void testCreateGemini() {
        ModelSpec spec = new ModelSpec();
        spec.setProvider("gemini");
        spec.setModelName("gemini-1.5-pro");
        spec.setApiKey("test-key");
        
        LangChainModelFactory factory = new LangChainModelFactory();
        ChatModel model = factory.create(spec);
        assertNotNull(model);
    }
}
