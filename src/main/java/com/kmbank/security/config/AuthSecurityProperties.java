package com.kmbank.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Type-safe configuration for anti brute-force protection.
 * Values are loaded from application.properties under prefix "auth.security".
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.security")
public class AuthSecurityProperties {

    /**
     * Maximum number of consecutive failed login attempts before temporary lockout.
     * Default: 5
     */
    private int maxFailedAttempts = 5;

    /**
     * Duration in minutes to temporarily lock the account after exceeding maxFailedAttempts.
     * Default: 15 minutes
     */
    private int lockDurationMinutes = 15;
}
