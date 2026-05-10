package com.aurora.backend.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", message);
    }

    public BusinessException(String code, String message) {
        this(HttpStatus.BAD_REQUEST, code, message);
    }

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
