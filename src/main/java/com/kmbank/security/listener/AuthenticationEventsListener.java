package com.kmbank.security.listener;

import com.kmbank.modules.user.enums.UserStatus;
import com.kmbank.modules.user.repository.UserRepository;
import com.kmbank.security.CustomUserPrincipal;
import com.kmbank.security.config.AuthSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEventsListener {

    private final UserRepository userRepository;
    private final AuthSecurityProperties authSecurityProperties;

    /**
     * On successful login:
     * - Reset failed login attempts to 0
     * - Clear temporary lock (lockedUntil)
     * - Record last login timestamp
     */
    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();

        if (principal instanceof CustomUserPrincipal customUserPrincipal) {
            log.debug("Authentication success for user: {}", customUserPrincipal.getUsername());

            // #1: Dùng ifPresentOrElse() để log warning khi không tìm thấy user trong DB
            userRepository.findById(customUserPrincipal.getId()).ifPresentOrElse(user -> {
                user.setLastLoginAt(Instant.now());
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }, () -> log.warn("[SECURITY] onSuccess: User ID not found in DB: {}", customUserPrincipal.getId()));

        } else {
            // #4: Log khi principal không phải CustomUserPrincipal
            log.warn("[SECURITY] onSuccess: Unexpected principal type: {}",
                    principal != null ? principal.getClass().getName() : "null");
        }
    }

    /**
     * On failed login (bad credentials):
     * - Increment failedLoginAttempts counter
     * - If counter reaches maxFailedAttempts, set lockedUntil to current time +
     * lockDurationMinutes
     * - Skip permanently locked or disabled accounts (no need to track further)
     */
    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String identifier = event.getAuthentication().getName();

        // #1: Dùng ifPresentOrElse() để log warning khi identifier không khớp user nào
        userRepository.findByUsernameOrPhoneNumber(identifier, identifier).ifPresentOrElse(user -> {

            // #6: Log khi skip tài khoản bị khóa/vô hiệu hóa vĩnh viễn
            if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DISABLED) {
                log.debug("[SECURITY] Skipping failed attempt tracking for {} account: {}",
                        user.getStatus(), maskUsername(user.getUsername()));
                return;
            }

            // #2: Kiểm tra lockedUntil - nếu đang bị khóa tạm thời thì không tăng counter
            // nữa
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
                // #3: Đổi log.debug() -> log.warn() vì đây là sự kiện bảo mật
                log.warn("[SECURITY] Failed login attempt {}/{} for identifier: {}",
                        newAttempts, authSecurityProperties.getMaxFailedAttempts(), maskIdentifier(identifier));
            }

            userRepository.save(user);

        }, () -> log.warn("[SECURITY] onFailure: No user found for identifier: {}", maskIdentifier(identifier)));
    }

    /**
     * Mask chuỗi để che thông tin PII (Ví dụ: "kientrung" -> "k***g")
     * Hiển thị ký tự đầu và ký tự cuối.
     */
    private String maskUsername(String username) {
        // #5: Thống nhất format mask: hiện 1 ký tự đầu + ký tự cuối
        if (username == null || username.length() <= 2)
            return "***";
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    /**
     * Mask identifier (username hoặc số điện thoại) để che thông tin PII
     * Hiển thị ký tự đầu và ký tự cuối.
     */
    private String maskIdentifier(String identifier) {
        // #5: Thống nhất format mask: hiện 1 ký tự đầu + ký tự cuối (giống
        // maskUsername)
        if (identifier == null || identifier.length() <= 2)
            return "***";
        return identifier.charAt(0) + "***" + identifier.charAt(identifier.length() - 1);
    }
}
