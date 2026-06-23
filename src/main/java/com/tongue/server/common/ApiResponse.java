package com.tongue.server.common;

public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        ApiResponse<T> response = success(data);
        response.setTraceId(traceId);
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message, String traceId) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.setCode(code);
        response.setMessage(message);
        response.setTraceId(traceId);
        return response;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
