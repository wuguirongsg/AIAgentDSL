package com.agentdsl.tools;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.ToolSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具扫描器测试。
 */
class ToolScannerTest {

    // --- 测试用 Bean ---

    static class SampleTools {

        @AgentTool(name = "getWeather", description = "查询指定城市天气")
        public String weather(@ToolParam(description = "城市名称") String city) {
            return city + ": 晴天 25°C";
        }

        @AgentTool(description = "获取当前时间")
        public String getCurrentTime() {
            return "2026-02-12 20:30:00";
        }

        @AgentTool(name = "add", description = "计算两数之和")
        public int add(@ToolParam(description = "第一个数") int a,
                @ToolParam(description = "第二个数") int b) {
            return a + b;
        }

        // 此方法没有 @AgentTool 注解，不应被扫描
        public String notATool() {
            return "ignored";
        }
    }

    @Test
    @DisplayName("应扫描到所有 @AgentTool 方法")
    void shouldScanAllAnnotatedMethods() {
        SampleTools bean = new SampleTools();
        List<ToolSpec> tools = ToolScanner.scan(bean);

        assertEquals(3, tools.size(), "应有 3 个工具");
    }

    @Test
    @DisplayName("name 属性覆盖方法名")
    void shouldUseAnnotationNameOverMethodName() {
        SampleTools bean = new SampleTools();
        List<ToolSpec> tools = ToolScanner.scan(bean);

        ToolSpec weather = tools.stream()
                .filter(t -> t.getName().equals("getWeather"))
                .findFirst().orElseThrow();

        assertEquals("查询指定城市天气", weather.getDescription());
        assertEquals(1, weather.getParameters().size());

        ParameterSpec param = weather.getParameters().get(0);
        assertEquals("城市名称", param.getDescription());
        assertEquals("string", param.getType());
    }

    @Test
    @DisplayName("无 name 属性时使用方法名")
    void shouldFallbackToMethodName() {
        SampleTools bean = new SampleTools();
        List<ToolSpec> tools = ToolScanner.scan(bean);

        boolean found = tools.stream().anyMatch(t -> t.getName().equals("getCurrentTime"));
        assertTrue(found, "应使用方法名 getCurrentTime 作为工具名");
    }

    @Test
    @DisplayName("多参数工具 — 参数类型映射正确")
    void shouldMapParameterTypes() {
        SampleTools bean = new SampleTools();
        List<ToolSpec> tools = ToolScanner.scan(bean);

        ToolSpec add = tools.stream()
                .filter(t -> t.getName().equals("add"))
                .findFirst().orElseThrow();

        assertEquals(2, add.getParameters().size());
        assertEquals("integer", add.getParameters().get(0).getType());
        assertEquals("integer", add.getParameters().get(1).getType());
        assertTrue(add.isBeanMethod(), "应标记为 Bean 方法");
    }
}
