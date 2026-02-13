package com.agentdsl.langchain4j;

import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.ToolSpec;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import groovy.lang.Closure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LangChainToolBridge 单元测试。
 * 验证 DSL ToolSpec 正确转换为 LangChain4j ToolSpecification + ToolExecutor。
 */
class LangChainToolBridgeTest {

    private LangChainToolBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new LangChainToolBridge();
    }

    @Test
    @DisplayName("转换无参数工具")
    void shouldConvertToolWithNoParameters() {
        ToolSpec spec = new ToolSpec("getCurrentTime");
        spec.setDescription("获取当前时间");
        spec.setExecuteBody(new Closure<String>(null) {
            @Override
            public String call() {
                return "2026-02-12T12:00:00";
            }

            @Override
            public int getMaximumNumberOfParameters() {
                return 0;
            }
        });

        LangChainToolBridge.ToolEntry entry = bridge.convert(spec);

        assertNotNull(entry.specification());
        assertEquals("getCurrentTime", entry.specification().name());
        assertEquals("获取当前时间", entry.specification().description());
        assertNull(entry.specification().parameters(), "无参数工具不应有 parameters");
    }

    @Test
    @DisplayName("转换带参数工具 — 验证 JsonObjectSchema 生成正确")
    void shouldConvertToolWithParameters() {
        ToolSpec spec = new ToolSpec("search");
        spec.setDescription("搜索工具");

        ParameterSpec p1 = new ParameterSpec();
        p1.setName("query");
        p1.setType("string");
        p1.setDescription("搜索关键词");
        p1.setRequired(true);

        ParameterSpec p2 = new ParameterSpec();
        p2.setName("limit");
        p2.setType("integer");
        p2.setRequired(false);

        spec.setParameters(List.of(p1, p2));
        spec.setExecuteBody(new Closure<String>(null) {
            @Override
            public String call(Object... args) {
                return "results";
            }
        });

        LangChainToolBridge.ToolEntry entry = bridge.convert(spec);
        ToolSpecification toolSpec = entry.specification();

        assertEquals("search", toolSpec.name());
        assertNotNull(toolSpec.parameters(), "应生成 parameters");

        JsonObjectSchema schema = toolSpec.parameters();
        assertNotNull(schema.properties());
        assertEquals(2, schema.properties().size());
        assertTrue(schema.properties().containsKey("query"));
        assertTrue(schema.properties().containsKey("limit"));

        // 验证 required 列表
        assertNotNull(schema.required());
        assertTrue(schema.required().contains("query"));
        assertFalse(schema.required().contains("limit"));
    }

    @Test
    @DisplayName("执行无参数工具 Closure")
    void shouldExecuteNoArgClosure() {
        ToolSpec spec = new ToolSpec("hello");
        spec.setDescription("测试工具");
        spec.setExecuteBody(new Closure<String>(null) {
            @Override
            public String call() {
                return "hello world";
            }

            @Override
            public int getMaximumNumberOfParameters() {
                return 0;
            }
        });

        LangChainToolBridge.ToolEntry entry = bridge.convert(spec);

        // 模拟工具执行
        dev.langchain4j.agent.tool.ToolExecutionRequest request = dev.langchain4j.agent.tool.ToolExecutionRequest
                .builder()
                .id("test-1")
                .name("hello")
                .arguments("{}")
                .build();

        String result = entry.executor().execute(request, "test-memory");
        assertEquals("hello world", result);
    }

    @Test
    @DisplayName("批量转换多个工具")
    void shouldConvertMultipleTools() {
        ToolSpec t1 = new ToolSpec("tool-a");
        t1.setDescription("工具A");
        t1.setExecuteBody(new Closure<String>(null) {
            @Override
            public String call() {
                return "a";
            }

            @Override
            public int getMaximumNumberOfParameters() {
                return 0;
            }
        });

        ToolSpec t2 = new ToolSpec("tool-b");
        t2.setDescription("工具B");
        t2.setExecuteBody(new Closure<String>(null) {
            @Override
            public String call() {
                return "b";
            }

            @Override
            public int getMaximumNumberOfParameters() {
                return 0;
            }
        });

        List<LangChainToolBridge.ToolEntry> entries = bridge.convertAll(List.of(t1, t2));

        assertEquals(2, entries.size());
        assertEquals("tool-a", entries.get(0).specification().name());
        assertEquals("tool-b", entries.get(1).specification().name());
    }
}
