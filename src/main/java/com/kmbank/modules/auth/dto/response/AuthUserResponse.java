package com.kmbank.modules.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserResponse {
    private UUID id;
    private String username;
    private String phoneNumber;
    private String fullName;
    private String role;
}
