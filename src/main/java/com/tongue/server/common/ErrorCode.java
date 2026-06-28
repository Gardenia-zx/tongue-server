package com.tongue.server.common;

public final class ErrorCode {

    public static final int BAD_REQUEST = 40000;
    public static final int IMAGE_TYPE_UNSUPPORTED = 40001;
    public static final int IMAGE_TOO_LARGE = 40002;
    public static final int IMAGE_EMPTY = 40003;
    public static final int AUTH_REQUIRED = 40101;
    public static final int TOKEN_INVALID = 40102;
    public static final int SMS_CODE_INVALID = 40103;
    public static final int ACCESS_DENIED = 40301;
    public static final int RESOURCE_NOT_FOUND = 40401;
    public static final int AGENT_THREAD_BUSY = 40901;
    public static final int TASK_NOT_RETRYABLE = 40902;
    public static final int AGENT_CALL_FAILED = 50001;
    public static final int MODEL_SERVICE_FAILED = 50002;
    public static final int RAG_UNAVAILABLE = 50003;
    public static final int REPORT_SAVE_FAILED = 50004;

    private ErrorCode() {
    }
}
