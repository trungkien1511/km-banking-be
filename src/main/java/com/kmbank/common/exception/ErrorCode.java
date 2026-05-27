package com.kmbank.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SUCCESS(HttpStatus.OK),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    USER_LOCKED(HttpStatus.UNAUTHORIZED),
    USER_DISABLED(HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
