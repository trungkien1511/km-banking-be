package com.kmbank.modules.auth.mapper;

import com.kmbank.modules.auth.dto.response.AuthUserResponse;
import com.kmbank.modules.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {
    public AuthUserResponse toAuthUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return AuthUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}
