package com.kmbank.security;

import com.kmbank.modules.user.entity.User;
import com.kmbank.modules.user.enums.UserStatus;
import com.kmbank.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
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

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));

        // Auto-unlock expired temporary lockout before handing off to Spring Security.
        // Only resets time-based locks; permanent LOCKED status requires admin action.
        if (isTemporaryLockExpired(user)) {
            log.debug("[SECURITY] Temporary lock expired for user: {} — resetting lock state", user.getUsername());
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        return new CustomUserPrincipal(user);
    }

    public UserDetails loadUserById(@NonNull UUID userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new CustomUserPrincipal(user);
    }

    /**
     * A temporary lock is considered expired when:
     * - lockedUntil is set (not null)
     * - The lock time has already passed
     * - The account is NOT permanently locked (status != LOCKED)
     */
    private boolean isTemporaryLockExpired(User user) {
        return user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(Instant.now())
                && user.getStatus() != UserStatus.LOCKED;
    }
}
