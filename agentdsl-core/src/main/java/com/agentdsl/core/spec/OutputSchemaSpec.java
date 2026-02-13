package com.agentdsl.core.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化输出定义规范。
 * 对应 DSL 中的 outputSchema { ... } 块。
 */
public class OutputSchemaSpec {

    private List<FieldSpec> fields = new ArrayList<>();

    public List<FieldSpec> getFields() {
        return fields;
    }

    public void setFields(List<FieldSpec> fields) {
        this.fields = fields;
    }

    public void addField(String name, String type, String description) {
        fields.add(new FieldSpec(name, type, description));
    }

    @Override
    public String toString() {
        return "OutputSchemaSpec{fields=" + fields + '}';
    }

    /**
     * 输出字段定义。
     */
    public static class FieldSpec {
        private final String name;
        private final String type;
        private final String description;

        public FieldSpec(String name, String type, String description) {
            this.name = name;
            this.type = type;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "FieldSpec{" + name + ":" + type + '}';
        }
    }
}
