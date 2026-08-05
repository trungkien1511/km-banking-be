package com.kmbank.security;

import com.kmbank.modules.customer.entity.Customer;
import com.kmbank.modules.customer.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;

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

        if (user.getStatus() == UserStatus.INACTIVE || user.getStatus() == UserStatus.LOCKED) {
            log.warn("[SECURITY] Attempted login on inactive/locked account: username={}, status={}",
                    user.getUsername(), user.getStatus());
        }

        UUID customerId = resolveCustomerId(user.getId());
        return new CustomUserPrincipal(user, customerId);
    }

    public UserDetails loadUserById(@NonNull UUID userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("[SECURITY] loadUserById failed: User ID not found: {}", userId);
                    return new UsernameNotFoundException(MSG_USER_NOT_FOUND);
                });

        if (user.getStatus() == UserStatus.INACTIVE) {
            log.warn("[SECURITY] loadUserById: Access denied for inactive user: ID={}", userId);
            throw new DisabledException("User account is inactive");
        }

        if (user.getStatus() == UserStatus.LOCKED
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))) {
            log.warn("[SECURITY] loadUserById: Access denied for locked user: ID={}", userId);
            throw new LockedException("User account is locked");
        }

        UUID customerId = resolveCustomerId(user.getId());
        return new CustomUserPrincipal(user, customerId);
    }

    private boolean isTemporaryLockExpired(User user) {
        return user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(Instant.now())
                && user.getStatus() != UserStatus.LOCKED
                && user.getStatus() != UserStatus.INACTIVE; // exclude permanently inactive accounts
    }

    /**
     * Resolves the customer profile UUID for the given user.
     * Returns {@code null} if no customer profile exists (e.g. admin/staff users).
     */
    private UUID resolveCustomerId(UUID userId) {
        return customerRepository.findByUserId(userId)
                .map(Customer::getId)
                .orElse(null);
    }
}
