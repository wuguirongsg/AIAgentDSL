package com.agentdsl.core.exception;

/**
 * DSL 运行时异常。
 * 当 Agent 执行期间发生错误时抛出。
 */
public class DslRuntimeException extends RuntimeException {

    private final String errorCode;

    public DslRuntimeException(String errorCode, String message) {
        super("[" + errorCode + "] " + message);
        this.errorCode = errorCode;
    }

    public DslRuntimeException(String errorCode, String message, Throwable cause) {
        super("[" + errorCode + "] " + message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
