package com.kmbank.modules.auth.controller;

import com.kmbank.common.dto.ApiResponse;
import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.auth.dto.request.LoginRequest;
import com.kmbank.modules.auth.dto.response.AuthUserResponse;
import com.kmbank.modules.auth.dto.response.LoginResponse;
import com.kmbank.modules.auth.service.AuthService;
import com.kmbank.security.CustomUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.kmbank.modules.auth.dto.request.RefreshTokenRequest;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("Login request received for identifier: {}", request.getIdentifier());
        LoginResponse response = authService.login(request, httpRequest);
        log.info("Login successful for user: {}", response.getUser().getUsername());
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserResponse>> getMe(@AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("REST request to get current user profile");

        if (principal == null) {
            log.warn("Unauthorized attempt to access /me endpoint");
            throw new BusinessException("User is not authenticated", ErrorCode.UNAUTHORIZED);
        }

        AuthUserResponse response = authService.getMe(principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Current user retrieved successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        log.debug("Refresh token request received");
        LoginResponse response = authService.refreshToken(request.getRefreshToken(), httpRequest);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        log.info("Logout request received");

        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
        }

        return ResponseEntity.ok(ApiResponse.emptySuccess("Logout successful"));
    }
}
