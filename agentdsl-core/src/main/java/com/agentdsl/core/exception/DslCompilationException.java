package com.agentdsl.core.exception;

/**
 * DSL 编译期异常。
 * 当 DSL 脚本存在语法或语义错误时抛出。
 */
public class DslCompilationException extends RuntimeException {

    private final String errorCode;

    public DslCompilationException(String errorCode, String message) {
        super("[" + errorCode + "] " + message);
        this.errorCode = errorCode;
    }

    public DslCompilationException(String errorCode, String message, Throwable cause) {
        super("[" + errorCode + "] " + message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
