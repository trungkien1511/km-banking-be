package com.kmbank.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Username or phone number is required")
    @Size(min = 3, max = 100, message = "Username or phone number is invalid")
    private String identifier;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be at least 8 characters")
    private String password;

    private Boolean rememberMe = false;
}
