package com.agentdsl.core.spec;

/**
 * 工具参数定义规范。
 * 对应 DSL 中的 parameter { ... } 块。
 */
public class ParameterSpec {

    private String name;
    private String type = "string";
    private String description;
    private boolean required = false;

    // v1.1+ 增强字段：参数约束
    private String pattern; // 正则校验表达式
    private Object defaultValue; // 参数默认值
    private String enumValues; // 枚举约束，逗号分隔 "value1,value2,value3"
    private Double min; // 数值最小值
    private Double max; // 数值最大值

    // --- Getters & Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(String enumValues) {
        this.enumValues = enumValues;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    @Override
    public String toString() {
        return "ParameterSpec{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                (pattern != null ? ", pattern='" + pattern + '\'' : "") +
                (defaultValue != null ? ", default=" + defaultValue : "") +
                (enumValues != null ? ", enum='" + enumValues + '\'' : "") +
                (min != null ? ", min=" + min : "") +
                (max != null ? ", max=" + max : "") +
                '}';
    }
}
