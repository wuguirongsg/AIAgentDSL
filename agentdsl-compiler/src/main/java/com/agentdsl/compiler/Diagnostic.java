package com.agentdsl.compiler;

/**
 * 表示在 DSL 编译或验证过程中产生的诊断信息（警告、提示等）。
 * 这种诊断不会中断编译，但会收集并在合适的时候反馈给用户。
 */
public class Diagnostic {

    public enum Severity {
        INFO,
        WARNING
    }

    private final Severity severity;
    private final String message;
    private final String target;

    public Diagnostic(Severity severity, String message, String target) {
        this.severity = severity;
        this.message = message;
        this.target = target;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (Target: %s)", severity.name(), message, target == null ? "Global" : target);
    }
}
