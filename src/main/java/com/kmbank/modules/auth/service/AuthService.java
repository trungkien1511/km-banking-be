package com.kmbank.modules.auth.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.auth.dto.request.LoginRequest;
import com.kmbank.modules.auth.dto.response.AuthUserResponse;
import com.kmbank.modules.auth.dto.response.LoginResponse;
import com.kmbank.modules.auth.mapper.AuthMapper;
import com.kmbank.modules.user.entity.User;
import com.kmbank.modules.user.entity.LoginHistory;
import com.kmbank.modules.user.entity.RefreshToken;
import com.kmbank.modules.user.repository.UserRepository;
import com.kmbank.modules.user.repository.LoginHistoryRepository;
import com.kmbank.modules.user.repository.RefreshTokenRepository;
import com.kmbank.security.CustomUserPrincipal;
import com.kmbank.security.jwt.JwtService;
import com.kmbank.security.utils.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;

/**
 * Service handling authentication operations such as login and retrieving
 * current user profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String MSG_USER_NOT_FOUND = "User not found";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthMapper authMapper;
    private final LoginHistoryRepository loginHistoryRepository;
    private final HttpRequestUtils httpRequestUtils;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.expiration-seconds}")
    private long jwtExpirationSeconds;

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String normalizedIdentifier = request.getIdentifier() != null ? request.getIdentifier().trim() : "";
        log.info("Attempting login for identifier: {}", normalizedIdentifier);

        try {
            Authentication authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(normalizedIdentifier, request.getPassword()));

            CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
            User user = principal.getUser();

            if (user == null) {
                log.error("Authentication succeeded but User entity is missing from principal");
                throw new BusinessException(MSG_USER_NOT_FOUND, ErrorCode.USER_NOT_FOUND);
            }

            String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(),
                    user.getRole().name());
            String refreshToken = jwtService.generateRefreshToken(user.getId());

            saveRefreshToken(user.getId(), refreshToken);
            saveLoginHistory(user.getId(), "SUCCESS", null, httpRequest);

            log.info("Login successful for user: {}", user.getUsername());

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtExpirationSeconds)
                    .user(authMapper.toAuthUserResponse(user))
                    .build();

        } catch (Exception e) {
            log.warn("Login failed for identifier: {} - Reason: {}", normalizedIdentifier, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public LoginResponse refreshToken(String refreshToken, HttpServletRequest httpRequest) {
        log.debug("Refresh token request received");

        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token", ErrorCode.UNAUTHORIZED));

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new BusinessException("Refresh token expired", ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new BusinessException("User not found", ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId());

        refreshTokenRepository.delete(storedToken);
        saveRefreshToken(user.getId(), newRefreshToken);

        log.info("Token rotated for user: {}", user.getUsername());
        saveLoginHistory(user.getId(), "REFRESHED", null, httpRequest);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(jwtService.getExpirationMs() / 1000)
                .refreshToken(newRefreshToken)
                .user(authMapper.toAuthUserResponse(user))
                .build();
    }

    private void saveRefreshToken(UUID userId, String refreshToken) {
        refreshTokenRepository.deleteByUserId(userId);

        String tokenHash = hashToken(refreshToken);

        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .revoked(false)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(token);
        log.debug("Refresh token saved for user: {}", userId);
    }

    public AuthUserResponse getMe(CustomUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            log.warn("getMe called with null or invalid principal");
            throw new BusinessException(MSG_USER_NOT_FOUND, ErrorCode.USER_NOT_FOUND);
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> {
                    log.warn("User ID {} not found in database", principal.getId());
                    return new BusinessException(MSG_USER_NOT_FOUND, ErrorCode.USER_NOT_FOUND);
                });

        return authMapper.toAuthUserResponse(user);
    }

    private void saveLoginHistory(UUID userId, String status, String failureReason, HttpServletRequest request) {
        try {
            LoginHistory history = LoginHistory.builder()
                    .userId(userId)
                    .ipAddress(httpRequestUtils.getClientIp(request))
                    .deviceName(httpRequestUtils.getDeviceName(request))
                    .userAgent(httpRequestUtils.getUserAgent(request))
                    .loginStatus(status)
                    .failureReason(failureReason)
                    .build();
            loginHistoryRepository.save(history);
            log.debug("Login history saved for user: {} - status: {}", userId, status);
        } catch (Exception e) {
            log.error("Failed to save login history for user: {}", userId, e);
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null) {
            log.warn("Logout called with null refresh token — nothing to revoke");
            return;
        }

        String tokenHash = hashToken(refreshToken);
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        log.info("User logged out, refresh token revoked");
    }
}
