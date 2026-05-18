package com.kmbank.modules.auth.controller;

import com.kmbank.common.dto.ApiResponse;
import com.kmbank.modules.auth.dto.request.LoginRequest;
import com.kmbank.modules.auth.dto.response.AuthUserResponse;
import com.kmbank.modules.auth.dto.response.LoginResponse;
import com.kmbank.modules.auth.service.AuthService;
import com.kmbank.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserResponse>> getMe(@AuthenticationPrincipal CustomUserPrincipal principal) {
        AuthUserResponse response = authService.getMe(principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Current user retrieved successfully"));
    }
}
