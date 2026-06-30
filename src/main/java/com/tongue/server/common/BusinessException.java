package com.tongue.server.common;

public class BusinessException extends RuntimeException {

    private final int code;
    private final String traceId;

    public BusinessException(int code, String message) {
        this(code, message, null, null);
    }

    public BusinessException(int code, String message, String traceId) {
        this(code, message, traceId, null);
    }

    public BusinessException(int code, String message, String traceId, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.traceId = traceId;
    }

    public int getCode() {
        return code;
    }

    public int getErrorCode() {
        return code;
    }

    public String getTraceId() {
        return traceId;
    }
}
