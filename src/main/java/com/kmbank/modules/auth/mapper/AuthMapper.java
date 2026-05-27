package com.kmbank.modules.auth.mapper;

import com.kmbank.modules.auth.dto.response.AuthUserResponse;
import com.kmbank.modules.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting User entity to Auth-related DTOs.
 */
@Slf4j // ← THÊM
@Component
public class AuthMapper {

    /**
     * Converts User entity to AuthUserResponse DTO.
     *
     * @param user the user entity (can be null)
     * @return AuthUserResponse DTO, or null if user is null
     */
    public AuthUserResponse toAuthUserResponse(User user) {
        if (user == null) {
            log.warn("Attempted to map null User to AuthUserResponse"); // ← THÊM
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