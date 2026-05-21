package com.kmbank.modules.auth.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.auth.dto.request.LoginRequest;
import com.kmbank.modules.auth.dto.response.AuthUserResponse;
import com.kmbank.modules.auth.dto.response.LoginResponse;
import com.kmbank.modules.auth.mapper.AuthMapper;
import com.kmbank.modules.user.entity.User;
import com.kmbank.modules.user.repository.UserRepository;
import com.kmbank.security.CustomUserPrincipal;
import com.kmbank.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling authentication operations such as login and retrieving
 * current user profile.
 */
@Slf4j // #1: Thêm logging
@Service
@RequiredArgsConstructor
public class AuthService {

    // #7: Định nghĩa constant cho thông báo lỗi tránh hardcode
    private static final String MSG_USER_NOT_FOUND = "User not found";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthMapper authMapper;

    // #5: Inject trực tiếp value expiration thay vì inject nguyên JwtProperties
    @Value("${jwt.expiration-seconds}")
    private long jwtExpirationSeconds;

    /**
     * Authenticates a user with credentials and generates a JWT token.
     *
     * @param request the login request payload containing identifier and password
     * @return the login response containing access token and user information
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedIdentifier = request.getIdentifier() != null ? request.getIdentifier().trim() : "";
        log.info("Attempting login for identifier: {}", normalizedIdentifier); // #8: Log audit khi đăng nhập

        try {
            // Authenticate user via Spring Security AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedIdentifier, request.getPassword()));

            // #3: Chỉ query DB 1 lần. Lấy trực tiếp User entity đã được load sẵn trong
            // Principal
            CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
            User user = principal.getUser();

            if (user == null) {
                // #2: Nhất quán exception - dùng BusinessException thay vì
                // UsernameNotFoundException
                log.error("Authentication succeeded but User entity is missing from principal");
                throw new BusinessException(MSG_USER_NOT_FOUND, ErrorCode.USER_NOT_FOUND);
            }

            // Generate token containing userId, username, and role
            String jwtToken = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());

            log.info("Login successful for user: {}", user.getUsername()); // #8: Log success

            return LoginResponse.builder()
                    .accessToken(jwtToken)
                    .expiresIn(jwtExpirationSeconds)
                    .user(authMapper.toAuthUserResponse(user))
                    .build();

        } catch (Exception e) {
            log.warn("Login failed for identifier: {} - Reason: {}", normalizedIdentifier, e.getMessage()); // #8: Log
                                                                                                            // failure
            throw e;
        }
    }

    /**
     * Retrieves the profile information of the currently authenticated user.
     *
     * @param principal the authenticated principal of the current user
     * @return the profile information
     */
    public AuthUserResponse getMe(CustomUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            log.warn("getMe called with null or invalid principal");
            throw new BusinessException(MSG_USER_NOT_FOUND, ErrorCode.USER_NOT_FOUND);
        }

        // #4: Kiểm tra user null trong getMe và dùng ErrorCode.USER_NOT_FOUND
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> {
                    log.warn("User ID {} not found in database", principal.getId());
                    return new BusinessException(MSG_USER_NOT_FOUND, ErrorCode.USER_NOT_FOUND);
                });

        return authMapper.toAuthUserResponse(user);
    }
}
