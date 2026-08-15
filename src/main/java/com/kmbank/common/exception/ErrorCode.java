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
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // Phase 2: Dashboard API error codes
    FORBIDDEN(HttpStatus.FORBIDDEN),
    INVALID_PAGE(HttpStatus.BAD_REQUEST),
    INVALID_LIMIT(HttpStatus.BAD_REQUEST),
    USER_INACTIVE(HttpStatus.FORBIDDEN),

    // Phase 3: Transaction write error codes
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    TRANSFER_SAME_ACCOUNT(HttpStatus.BAD_REQUEST),
    ACCOUNT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
