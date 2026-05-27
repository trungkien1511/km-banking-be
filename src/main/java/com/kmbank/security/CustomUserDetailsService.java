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

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final String MSG_INVALID_CREDENTIALS = "Invalid username or password";
    private static final String MSG_USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> {
                    log.warn("[SECURITY] Login failed: User not found for identifier: {}", identifier);
                    return new UsernameNotFoundException(MSG_INVALID_CREDENTIALS);
                });

        if (isTemporaryLockExpired(user)) {
            log.info("[SECURITY] Temporary lock expired for user: {} — resetting lock state", user.getUsername());
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        if (user.getStatus() == UserStatus.DISABLED || user.getStatus() == UserStatus.LOCKED) {
            log.warn("[SECURITY] Attempted login on inactive/locked account: username={}, status={}",
                    user.getUsername(), user.getStatus());
        }

        return new CustomUserPrincipal(user);
    }

    public UserDetails loadUserById(@NonNull UUID userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("[SECURITY] loadUserById failed: User ID not found: {}", userId);
                    return new UsernameNotFoundException(MSG_USER_NOT_FOUND);
                });

        if (user.getStatus() == UserStatus.DISABLED) {
            log.warn("[SECURITY] loadUserById: Access denied for disabled user: ID={}", userId);
            throw new DisabledException("User account is disabled");
        }

        if (user.getStatus() == UserStatus.LOCKED
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))) {
            log.warn("[SECURITY] loadUserById: Access denied for locked user: ID={}", userId);
            throw new LockedException("User account is locked");
        }

        return new CustomUserPrincipal(user);
    }

    private boolean isTemporaryLockExpired(User user) {
        return user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(Instant.now())
                && user.getStatus() != UserStatus.LOCKED
                && user.getStatus() != UserStatus.DISABLED; // #1: Sửa lỗi bỏ qua Disabled
    }
}
