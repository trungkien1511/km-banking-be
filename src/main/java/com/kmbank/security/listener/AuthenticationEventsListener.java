package com.kmbank.security.listener;

import com.kmbank.modules.user.repository.UserRepository;
import com.kmbank.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEventsListener {

    private final UserRepository userRepository;

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        
        if (principal instanceof CustomUserPrincipal customUserPrincipal) {
            log.debug("Authentication success event triggered for user: {}", customUserPrincipal.getUsername());
            
            userRepository.findById(customUserPrincipal.getId()).ifPresent(user -> {
                user.setLastLoginAt(Instant.now());
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            });
        }
    }
}
