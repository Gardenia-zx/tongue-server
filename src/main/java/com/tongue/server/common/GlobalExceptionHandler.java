package com.tongue.server.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException ex) {
        HttpStatus status;
        if (ex.getCode() == ErrorCode.AUTH_REQUIRED || ex.getCode() == ErrorCode.TOKEN_INVALID) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (ex.getCode() == ErrorCode.ACCESS_DENIED) {
            status = HttpStatus.FORBIDDEN;
        } else if (ex.getCode() == ErrorCode.RESOURCE_NOT_FOUND) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex.getCode() == ErrorCode.AGENT_THREAD_BUSY
                || ex.getCode() == ErrorCode.TASK_NOT_RETRYABLE) {
            status = HttpStatus.CONFLICT;
        } else if (ex.getCode() >= 50000) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getTraceId()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentNotValidException.class,
            MultipartException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(Exception ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(40000, ex.getMessage(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ErrorCode.IMAGE_TOO_LARGE, "图片大小超过限制", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "服务器内部错误", null));
    }
}
