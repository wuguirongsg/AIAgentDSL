package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.ToolSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DslValidator 增强校验规则的单元测试。
 * 覆盖 returnType、timeout、pattern、min/max、enumValues 校验。
 */
class DslValidatorEnhancedTest {

    /**
     * 创建一个合法的最小化 ToolSpec（用于测试单个校验规则）。
     */
    private ToolSpec createValidTool(String name) {
        ToolSpec tool = new ToolSpec(name);
        tool.setDescription("测试工具");
        tool.setExecuteBody("mock-closure");
        return tool;
    }

    @Nested
    @DisplayName("returnType 校验")
    class ReturnTypeValidation {

        @Test
        @DisplayName("合法的 returnType 应通过")
        void shouldPassWithValidReturnTypes() {
            for (String type : new String[] { "string", "json", "object", "number", "boolean", "array" }) {
                ToolSpec tool = createValidTool("tool-" + type);
                tool.setReturnType(type);
                assertDoesNotThrow(() -> DslValidator.validateTool(tool));
            }
        }

        @Test
        @DisplayName("不设置 returnType (null) 应通过")
        void shouldPassWithNullReturnType() {
            ToolSpec tool = createValidTool("null-return");
            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("非法 returnType 应产生 Warning Diagnostic")
        void shouldProduceWarningOnInvalidReturnType() {
            ToolSpec tool = createValidTool("bad-return");
            tool.setReturnType("invalid_type");

            java.util.List<Diagnostic> diagnostics = new java.util.ArrayList<>();
            assertDoesNotThrow(() -> DslValidator.validateTool(tool, diagnostics));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(Diagnostic.Severity.WARNING, diag.getSeverity());
            assertTrue(diag.getMessage().contains("returnType"));
            assertTrue(diag.getMessage().contains("invalid_type"));
        }
    }

    @Nested
    @DisplayName("timeoutSeconds 校验")
    class TimeoutValidation {

        @Test
        @DisplayName("默认超时 (30s) 应通过")
        void shouldPassWithDefaultTimeout() {
            ToolSpec tool = createValidTool("default-timeout");
            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("边界值 1 和 300 应通过")
        void shouldPassWithBoundaryValues() {
            ToolSpec tool1 = createValidTool("min-timeout");
            tool1.setTimeoutSeconds(1);
            assertDoesNotThrow(() -> DslValidator.validateTool(tool1));

            ToolSpec tool2 = createValidTool("max-timeout");
            tool2.setTimeoutSeconds(300);
            assertDoesNotThrow(() -> DslValidator.validateTool(tool2));
        }

        @Test
        @DisplayName("超时为 0 应产生 Warning Diagnostic")
        void shouldProduceWarningOnZeroTimeout() {
            ToolSpec tool = createValidTool("zero-timeout");
            tool.setTimeoutSeconds(0);

            java.util.List<Diagnostic> diagnostics = new java.util.ArrayList<>();
            assertDoesNotThrow(() -> DslValidator.validateTool(tool, diagnostics));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(Diagnostic.Severity.WARNING, diag.getSeverity());
            assertTrue(diag.getMessage().contains("timeoutSeconds"));
        }

        @Test
        @DisplayName("超时超过 300 应产生 Warning Diagnostic")
        void shouldProduceWarningOnOversizedTimeout() {
            ToolSpec tool = createValidTool("huge-timeout");
            tool.setTimeoutSeconds(301);

            java.util.List<Diagnostic> diagnostics = new java.util.ArrayList<>();
            assertDoesNotThrow(() -> DslValidator.validateTool(tool, diagnostics));

            assertEquals(1, diagnostics.size());
            Diagnostic diag = diagnostics.get(0);
            assertEquals(Diagnostic.Severity.WARNING, diag.getSeverity());
            assertTrue(diag.getMessage().contains("timeoutSeconds"));
        }
    }

    @Nested
    @DisplayName("参数 pattern 校验")
    class PatternValidation {

        @Test
        @DisplayName("合法正则表达式应通过")
        void shouldPassWithValidPattern() {
            ToolSpec tool = createValidTool("pattern-tool");
            ParameterSpec param = new ParameterSpec();
            param.setName("orderId");
            param.setPattern("ORD-\\d{4,10}");
            tool.getParameters().add(param);

            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("不设置 pattern (null) 应通过")
        void shouldPassWithNullPattern() {
            ToolSpec tool = createValidTool("no-pattern");
            ParameterSpec param = new ParameterSpec();
            param.setName("name");
            tool.getParameters().add(param);

            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("非法正则表达式应抛出 ADSL-002")
        void shouldThrowOnInvalidPattern() {
            ToolSpec tool = createValidTool("bad-pattern");
            ParameterSpec param = new ParameterSpec();
            param.setName("input");
            param.setPattern("[invalid(");
            tool.getParameters().add(param);

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> DslValidator.validateTool(tool));
            assertTrue(ex.getMessage().contains("ADSL-002"));
            assertTrue(ex.getMessage().contains("pattern"));
            assertTrue(ex.getMessage().contains("input"));
        }
    }

    @Nested
    @DisplayName("参数 min/max 校验")
    class MinMaxValidation {

        @Test
        @DisplayName("min < max 应通过")
        void shouldPassWhenMinLessThanMax() {
            ToolSpec tool = createValidTool("valid-range");
            ParameterSpec param = new ParameterSpec();
            param.setName("limit");
            param.setMin(1.0);
            param.setMax(100.0);
            tool.getParameters().add(param);

            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("min == max 应通过")
        void shouldPassWhenMinEqualsMax() {
            ToolSpec tool = createValidTool("equal-range");
            ParameterSpec param = new ParameterSpec();
            param.setName("fixed");
            param.setMin(50.0);
            param.setMax(50.0);
            tool.getParameters().add(param);

            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("只设 min 不设 max 应通过")
        void shouldPassWithOnlyMin() {
            ToolSpec tool = createValidTool("min-only");
            ParameterSpec param = new ParameterSpec();
            param.setName("count");
            param.setMin(0.0);
            tool.getParameters().add(param);

            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("min > max 应抛出 ADSL-002")
        void shouldThrowWhenMinGreaterThanMax() {
            ToolSpec tool = createValidTool("bad-range");
            ParameterSpec param = new ParameterSpec();
            param.setName("score");
            param.setMin(100.0);
            param.setMax(1.0);
            tool.getParameters().add(param);

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> DslValidator.validateTool(tool));
            assertTrue(ex.getMessage().contains("ADSL-002"));
            assertTrue(ex.getMessage().contains("min"));
            assertTrue(ex.getMessage().contains("max"));
            assertTrue(ex.getMessage().contains("score"));
        }
    }

    @Nested
    @DisplayName("参数 enumValues 校验")
    class EnumValuesValidation {

        @Test
        @DisplayName("正常 enumValues 应通过")
        void shouldPassWithValidEnumValues() {
            ToolSpec tool = createValidTool("enum-tool");
            ParameterSpec param = new ParameterSpec();
            param.setName("status");
            param.setEnumValues("pending,active,completed");
            tool.getParameters().add(param);

            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("不设置 enumValues (null) 应通过")
        void shouldPassWithNullEnumValues() {
            ToolSpec tool = createValidTool("no-enum");
            ParameterSpec param = new ParameterSpec();
            param.setName("text");
            tool.getParameters().add(param);

            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("空字符串 enumValues 应抛出 ADSL-002")
        void shouldThrowOnEmptyEnumValues() {
            ToolSpec tool = createValidTool("empty-enum");
            ParameterSpec param = new ParameterSpec();
            param.setName("type");
            param.setEnumValues("");
            tool.getParameters().add(param);

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> DslValidator.validateTool(tool));
            assertTrue(ex.getMessage().contains("ADSL-002"));
            assertTrue(ex.getMessage().contains("enumValues"));
            assertTrue(ex.getMessage().contains("type"));
        }

        @Test
        @DisplayName("空白字符串 enumValues 应抛出 ADSL-002")
        void shouldThrowOnBlankEnumValues() {
            ToolSpec tool = createValidTool("blank-enum");
            ParameterSpec param = new ParameterSpec();
            param.setName("category");
            param.setEnumValues("   ");
            tool.getParameters().add(param);

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> DslValidator.validateTool(tool));
            assertTrue(ex.getMessage().contains("ADSL-002"));
        }
    }

    @Nested
    @DisplayName("多参数复合校验")
    class MultiParameterValidation {

        @Test
        @DisplayName("多个合法参数应全部通过")
        void shouldPassWithMultipleValidParameters() {
            ToolSpec tool = createValidTool("multi-param");

            ParameterSpec p1 = new ParameterSpec();
            p1.setName("query");
            p1.setPattern("\\w+");
            tool.getParameters().add(p1);

            ParameterSpec p2 = new ParameterSpec();
            p2.setName("limit");
            p2.setMin(1.0);
            p2.setMax(100.0);
            tool.getParameters().add(p2);

            ParameterSpec p3 = new ParameterSpec();
            p3.setName("format");
            p3.setEnumValues("json,xml,csv");
            p3.setDefaultValue("json");
            tool.getParameters().add(p3);

            assertDoesNotThrow(() -> DslValidator.validateTool(tool));
        }

        @Test
        @DisplayName("第二个参数非法时应报告正确的参数名")
        void shouldReportCorrectParameterName() {
            ToolSpec tool = createValidTool("mixed-params");

            ParameterSpec valid = new ParameterSpec();
            valid.setName("good");
            valid.setPattern("\\w+");
            tool.getParameters().add(valid);

            ParameterSpec invalid = new ParameterSpec();
            invalid.setName("bad");
            invalid.setPattern("[broken(");
            tool.getParameters().add(invalid);

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> DslValidator.validateTool(tool));
            assertTrue(ex.getMessage().contains("bad"));
            assertFalse(ex.getMessage().contains("good"));
        }
    }
}
