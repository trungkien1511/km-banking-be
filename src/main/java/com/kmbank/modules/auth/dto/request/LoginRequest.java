package com.kmbank.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object (DTO) for handling user login requests.
 * Contains validation rules to ensure data integrity before reaching the service layer.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * Tên đăng nhập hoặc số điện thoại của người dùng.
     */
    @NotBlank(message = "Username or phone number is required")
    @Size(min = 3, max = 100, message = "Identifier must be between 3 and 100 characters")
    private String identifier;

    /**
     * Mật khẩu của người dùng.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    /**
     * Cờ đánh dấu người dùng muốn duy trì trạng thái đăng nhập.
     * (Placeholder cho tính năng Refresh Token / Remember Me sau này).
     */
    @Builder.Default
    private Boolean rememberMe = false;
}
