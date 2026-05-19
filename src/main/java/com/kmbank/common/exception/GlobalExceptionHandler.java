package com.kmbank.common.exception;

import com.kmbank.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.DisabledException;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ex.getErrorCode().name(), ex.getMessage()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
                List<ApiResponse.FieldError> validationErrors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(err -> new ApiResponse.FieldError(err.getField(), err.getDefaultMessage()))
                                .collect(Collectors.toList());

                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .body(ApiResponse.validationError(validationErrors));
        }

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(ErrorCode.INVALID_CREDENTIALS.name(),
                                                "Invalid username or password"));
        }

        @ExceptionHandler(LockedException.class)
        public ResponseEntity<ApiResponse<Void>> handleLockedException(LockedException ex) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(ErrorCode.USER_LOCKED.name(), "User account is locked"));
        }

        @ExceptionHandler(DisabledException.class)
        public ResponseEntity<ApiResponse<Void>> handleDisabledException(DisabledException ex) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(ErrorCode.USER_DISABLED.name(), "User account is disabled"));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error(ErrorCode.ACCESS_DENIED.name(),
                                                "You do not have the required permissions to access this resource"));
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.name(), "Authentication failed"));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(
                                                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                                                "An unexpected error occurred on our end. Please contact support."));
        }
}
