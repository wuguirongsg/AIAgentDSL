package com.agentdsl.core.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolSpec、ParameterSpec、PermissionSpec 增强字段的单元测试。
 */
class SpecModelTest {

    @Nested
    @DisplayName("ToolSpec 增强字段")
    class ToolSpecEnhancements {

        @Test
        @DisplayName("默认值：timeoutSeconds 应为 30")
        void shouldHaveDefaultTimeout() {
            ToolSpec tool = new ToolSpec("test");
            assertEquals(30, tool.getTimeoutSeconds());
        }

        @Test
        @DisplayName("设置 returnType 和 returnDescription")
        void shouldSetReturnTypeAndDescription() {
            ToolSpec tool = new ToolSpec("orderQuery");
            tool.setReturnType("json");
            tool.setReturnDescription("包含 orderId, status 的 JSON");

            assertEquals("json", tool.getReturnType());
            assertEquals("包含 orderId, status 的 JSON", tool.getReturnDescription());
        }

        @Test
        @DisplayName("设置 timeoutSeconds")
        void shouldSetTimeout() {
            ToolSpec tool = new ToolSpec("slow-tool");
            tool.setTimeoutSeconds(120);
            assertEquals(120, tool.getTimeoutSeconds());
        }

        @Test
        @DisplayName("设置 onErrorHandler")
        void shouldSetOnErrorHandler() {
            ToolSpec tool = new ToolSpec("risky-tool");
            Object handler = "mock-closure";
            tool.setOnErrorHandler(handler);
            assertEquals("mock-closure", tool.getOnErrorHandler());
        }

        @Test
        @DisplayName("设置 permissions")
        void shouldSetPermissions() {
            ToolSpec tool = new ToolSpec("http-tool");
            PermissionSpec perm = new PermissionSpec();
            perm.addNetworkPattern("https://api.example.com/*");
            tool.setPermissions(perm);

            assertNotNull(tool.getPermissions());
            assertEquals(1, tool.getPermissions().getNetworkPatterns().size());
            assertEquals("https://api.example.com/*",
                    tool.getPermissions().getNetworkPatterns().get(0));
        }

        @Test
        @DisplayName("toString 包含 timeout 和 returnType")
        void shouldIncludeNewFieldsInToString() {
            ToolSpec tool = new ToolSpec("test");
            tool.setDescription("desc");
            tool.setReturnType("string");

            String str = tool.toString();
            assertTrue(str.contains("timeout=30s"));
            assertTrue(str.contains("returns=string"));
        }

        @Test
        @DisplayName("returnType 为 null 时 toString 不显示")
        void shouldOmitNullReturnTypeInToString() {
            ToolSpec tool = new ToolSpec("test");
            tool.setDescription("desc");

            String str = tool.toString();
            assertFalse(str.contains("returns="));
        }
    }

    @Nested
    @DisplayName("ParameterSpec 增强字段")
    class ParameterSpecEnhancements {

        @Test
        @DisplayName("设置 pattern 正则校验")
        void shouldSetPattern() {
            ParameterSpec param = new ParameterSpec();
            param.setName("orderId");
            param.setPattern("ORD-\\d{4,10}");

            assertEquals("ORD-\\d{4,10}", param.getPattern());
        }

        @Test
        @DisplayName("设置 defaultValue")
        void shouldSetDefaultValue() {
            ParameterSpec param = new ParameterSpec();
            param.setName("includeItems");
            param.setDefaultValue(true);

            assertEquals(true, param.getDefaultValue());
        }

        @Test
        @DisplayName("设置 String 类型 defaultValue")
        void shouldSetStringDefaultValue() {
            ParameterSpec param = new ParameterSpec();
            param.setName("format");
            param.setDefaultValue("json");

            assertEquals("json", param.getDefaultValue());
        }

        @Test
        @DisplayName("设置 enumValues")
        void shouldSetEnumValues() {
            ParameterSpec param = new ParameterSpec();
            param.setName("status");
            param.setEnumValues("pending,active,completed");

            assertEquals("pending,active,completed", param.getEnumValues());
        }

        @Test
        @DisplayName("设置 min 和 max")
        void shouldSetMinMax() {
            ParameterSpec param = new ParameterSpec();
            param.setName("limit");
            param.setMin(1.0);
            param.setMax(100.0);

            assertEquals(1.0, param.getMin());
            assertEquals(100.0, param.getMax());
        }

        @Test
        @DisplayName("min/max 默认为 null")
        void shouldHaveNullMinMaxByDefault() {
            ParameterSpec param = new ParameterSpec();
            assertNull(param.getMin());
            assertNull(param.getMax());
        }

        @Test
        @DisplayName("toString 包含新字段")
        void shouldIncludeNewFieldsInToString() {
            ParameterSpec param = new ParameterSpec();
            param.setName("id");
            param.setType("string");
            param.setPattern("ORD-\\d+");
            param.setEnumValues("a,b,c");
            param.setMin(1.0);
            param.setMax(10.0);
            param.setDefaultValue("default");

            String str = param.toString();
            assertTrue(str.contains("pattern='ORD-\\d+'"));
            assertTrue(str.contains("enum='a,b,c'"));
            assertTrue(str.contains("min=1.0"));
            assertTrue(str.contains("max=10.0"));
            assertTrue(str.contains("default=default"));
        }
    }

    @Nested
    @DisplayName("PermissionSpec")
    class PermissionSpecTests {

        @Test
        @DisplayName("添加 network pattern")
        void shouldAddNetworkPattern() {
            PermissionSpec perm = new PermissionSpec();
            perm.addNetworkPattern("https://api.example.com/*");
            perm.addNetworkPattern("https://cdn.example.com/*");

            assertEquals(2, perm.getNetworkPatterns().size());
        }

        @Test
        @DisplayName("添加 file pattern")
        void shouldAddFilePattern() {
            PermissionSpec perm = new PermissionSpec();
            perm.addFilePattern("/tmp/*");
            perm.addFilePattern("/data/output/*");

            assertEquals(2, perm.getFilePatterns().size());
        }

        @Test
        @DisplayName("添加 database")
        void shouldAddDatabase() {
            PermissionSpec perm = new PermissionSpec();
            perm.addDatabase("orders");
            perm.addDatabase("users");

            assertEquals(2, perm.getDatabases().size());
            assertTrue(perm.getDatabases().contains("orders"));
        }

        @Test
        @DisplayName("默认列表为空")
        void shouldHaveEmptyListsByDefault() {
            PermissionSpec perm = new PermissionSpec();
            assertTrue(perm.getNetworkPatterns().isEmpty());
            assertTrue(perm.getFilePatterns().isEmpty());
            assertTrue(perm.getDatabases().isEmpty());
        }

        @Test
        @DisplayName("toString 显示计数")
        void shouldShowCountsInToString() {
            PermissionSpec perm = new PermissionSpec();
            perm.addNetworkPattern("url1");
            perm.addFilePattern("path1");
            perm.addFilePattern("path2");

            String str = perm.toString();
            assertTrue(str.contains("network=1"));
            assertTrue(str.contains("files=2"));
            assertTrue(str.contains("databases=0"));
        }
    }
}
