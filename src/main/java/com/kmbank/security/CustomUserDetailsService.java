package com.kmbank.security;

import com.kmbank.modules.user.entity.User;
import com.kmbank.modules.user.enums.UserStatus;
import com.kmbank.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Custom implementation of UserDetailsService to load user details from database and handle automatic account unlocking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // #5: Định nghĩa constant cho error message để tránh magic strings
    private static final String MSG_INVALID_CREDENTIALS = "Invalid username or password";
    private static final String MSG_USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // #2: Log warning chi tiết khi không tìm thấy user trước khi quăng exception chung chung bảo mật
        User user = userRepository.findByUsernameOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> {
                    log.warn("[SECURITY] Login failed: User not found for identifier: {}", identifier);
                    return new UsernameNotFoundException(MSG_INVALID_CREDENTIALS);
                });

        // Auto-unlock expired temporary lockout before handing off to Spring Security.
        // Only resets time-based locks; permanent LOCKED status requires admin action.
        if (isTemporaryLockExpired(user)) {
            log.info("[SECURITY] Temporary lock expired for user: {} — resetting lock state", user.getUsername());
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        // #3 và #7: Thêm log warning khi đăng nhập vào tài khoản bị DISABLED hoặc LOCKED vĩnh viễn
        if (user.getStatus() == UserStatus.DISABLED || user.getStatus() == UserStatus.LOCKED) {
            log.warn("[SECURITY] Attempted login on inactive/locked account: username={}, status={}",
                    user.getUsername(), user.getStatus());
        }

        return new CustomUserPrincipal(user);
    }

    /**
     * Loads user details by their database UUID. Also validates if the user status is active.
     *
     * @param userId UUID of the user
     * @return UserDetails implementation for the user
     * @throws UsernameNotFoundException if user is not found
     * @throws DisabledException if user account is disabled
     * @throws LockedException if user account is locked or temporarily locked
     */
    // #6: Thêm JavaDoc chi tiết cho loadUserById
    public UserDetails loadUserById(@NonNull UUID userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("[SECURITY] loadUserById failed: User ID not found: {}", userId);
                    return new UsernameNotFoundException(MSG_USER_NOT_FOUND);
                });

        // #4: Kiểm tra status trong loadUserById để từ chối các JWT cũ của tài khoản bị khóa/vô hiệu hóa
        if (user.getStatus() == UserStatus.DISABLED) {
            log.warn("[SECURITY] loadUserById: Access denied for disabled user: ID={}", userId);
            throw new DisabledException("User account is disabled");
        }

        if (user.getStatus() == UserStatus.LOCKED || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))) {
            log.warn("[SECURITY] loadUserById: Access denied for locked user: ID={}", userId);
            throw new LockedException("User account is locked");
        }

        return new CustomUserPrincipal(user);
    }

    /**
     * A temporary lock is considered expired when:
     * - lockedUntil is set (not null)
     * - The lock time has already passed
     * - The account is NOT permanently locked (status != LOCKED)
     * - The account is NOT disabled (status != DISABLED) (#1: THÊM)
     */
    private boolean isTemporaryLockExpired(User user) {
        return user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(Instant.now())
                && user.getStatus() != UserStatus.LOCKED
                && user.getStatus() != UserStatus.DISABLED; // #1: Sửa lỗi bỏ qua Disabled
    }
}
