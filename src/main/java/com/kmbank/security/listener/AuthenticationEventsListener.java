package com.kmbank.security.listener;

import com.kmbank.modules.user.entity.LoginHistory;
import com.kmbank.modules.user.enums.UserStatus;
import com.kmbank.modules.user.repository.UserRepository;
import com.kmbank.modules.user.repository.LoginHistoryRepository;
import com.kmbank.security.CustomUserPrincipal;
import com.kmbank.security.config.AuthSecurityProperties;
import com.kmbank.security.utils.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEventsListener {

    private final UserRepository userRepository;
    private final AuthSecurityProperties authSecurityProperties;
    private final LoginHistoryRepository loginHistoryRepository;
    private final HttpRequestUtils httpRequestUtils;

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();

        if (principal instanceof CustomUserPrincipal customUserPrincipal) {
            log.debug("Authentication success for user: {}", customUserPrincipal.getUsername());

            userRepository.findById(customUserPrincipal.getId()).ifPresentOrElse(user -> {
                user.setLastLoginAt(Instant.now());
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }, () -> log.warn("[SECURITY] onSuccess: User ID not found in DB: {}", customUserPrincipal.getId()));

        } else {
            log.warn("[SECURITY] onSuccess: Unexpected principal type: {}",
                    principal != null ? principal.getClass().getName() : "null");
        }
    }

    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String identifier = event.getAuthentication().getName();
        log.warn("Failed login attempt for identifier: {}", maskIdentifier(identifier));

        HttpServletRequest request = getCurrentHttpRequest();

        userRepository.findByUsernameOrPhoneNumber(identifier, identifier).ifPresentOrElse(user -> {
            saveLoginHistory(user.getId(), "FAILED", "Bad credentials", request);

            if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DISABLED) {
                log.debug("[SECURITY] Skipping failed attempt tracking for {} account: {}",
                        user.getStatus(), maskUsername(user.getUsername()));
                return;
            }

            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
                log.debug("[SECURITY] Account already temporarily locked, skipping counter increment: {}",
                        maskUsername(user.getUsername()));
                return;
            }

            int newAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(newAttempts);

            if (newAttempts >= authSecurityProperties.getMaxFailedAttempts()) {
                Instant lockUntil = Instant.now()
                        .plus(authSecurityProperties.getLockDurationMinutes(), ChronoUnit.MINUTES);
                user.setLockedUntil(lockUntil);
                log.warn("[SECURITY] Account temporarily locked after {} failed attempts: username={}",
                        newAttempts, maskUsername(user.getUsername()));
            } else {
                log.warn("[SECURITY] Failed login attempt {}/{} for identifier: {}",
                        newAttempts, authSecurityProperties.getMaxFailedAttempts(), maskIdentifier(identifier));
            }

            userRepository.save(user);

        }, () -> {
            log.warn("[SECURITY] onFailure: No user found for identifier: {}", maskIdentifier(identifier));
            saveLoginHistoryForUnknownIdentifier(identifier, request);
        });
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private void saveLoginHistory(UUID userId, String status, String failureReason, HttpServletRequest request) {
        try {
            if (request == null)
                return;

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

    private void saveLoginHistoryForUnknownIdentifier(String identifier, HttpServletRequest request) {
        try {
            if (request == null)
                return;

            LoginHistory history = LoginHistory.builder()
                    .userId(null)
                    .ipAddress(httpRequestUtils.getClientIp(request))
                    .deviceName(httpRequestUtils.getDeviceName(request))
                    .userAgent(httpRequestUtils.getUserAgent(request))
                    .loginStatus("FAILED")
                    .failureReason("User not found: " + maskIdentifier(identifier))
                    .build();
            loginHistoryRepository.save(history);
            log.debug("Login history saved for unknown identifier: {}", maskIdentifier(identifier));
        } catch (Exception e) {
            log.error("Failed to save login history for unknown identifier", e);
        }
    }

    private String maskUsername(String username) {
        if (username == null || username.length() <= 2)
            return "***";
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() <= 2)
            return "***";
        return identifier.charAt(0) + "***" + identifier.charAt(identifier.length() - 1);
    }
}
