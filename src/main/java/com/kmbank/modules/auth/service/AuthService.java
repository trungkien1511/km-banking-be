package com.kmbank.modules.auth.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.auth.dto.request.LoginRequest;
import com.kmbank.modules.auth.dto.response.AuthUserResponse;
import com.kmbank.modules.auth.dto.response.LoginResponse;
import com.kmbank.modules.user.entity.User;
import com.kmbank.modules.user.repository.UserRepository;
import com.kmbank.security.CustomUserPrincipal;
import com.kmbank.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${jwt.expiration-seconds}")
    private long jwtExpirationSeconds;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Authenticate user via Spring Security AuthenticationManager
        // Account lock (LockedException) and disable (DisabledException) states
        // are automatically validated here using CustomUserPrincipal methods.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getIdentifier(), request.getPassword())
        );

        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Update last login and reset failed login attempts
        user.setLastLoginAt(Instant.now());
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        // Generate token containing userId, username, and role
        String jwtToken = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());

        return LoginResponse.builder()
                .accessToken(jwtToken)
                .expiresIn(jwtExpirationSeconds)
                .user(toAuthUserResponse(user))
                .build();
    }

    public AuthUserResponse getMe(CustomUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("User not found", ErrorCode.INTERNAL_SERVER_ERROR));

        return toAuthUserResponse(user);
    }

    private AuthUserResponse toAuthUserResponse(User user) {
        return AuthUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}
