package com.aurora.backend.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public NotFoundException(String resourceName, Object identifier) {
        this(resourceName + " not found with identifier: " + identifier);
    }
}
