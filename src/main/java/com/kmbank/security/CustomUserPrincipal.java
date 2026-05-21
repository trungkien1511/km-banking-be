package com.kmbank.security;

import com.kmbank.modules.user.entity.User;
import com.kmbank.modules.user.enums.UserStatus;
import lombok.Getter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

@Getter
public class CustomUserPrincipal implements UserDetails {

    @NonNull
    private final UUID id;
    private final String username;
    private final String password;
    private final UserStatus status;
    private final Instant lockedUntil;
    private final Collection<? extends GrantedAuthority> authorities;
    private final User user;

    public CustomUserPrincipal(User user) {
        this.id = Objects.requireNonNull(user.getId(), "User ID cannot be null");
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.status = user.getStatus();
        this.lockedUntil = user.getLockedUntil();
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Returns false if:
     * - status is LOCKED (permanent lockout, requires admin action)
     * - lockedUntil is set and still in the future (temporary lockout due to brute-force)
     */
    @Override
    public boolean isAccountNonLocked() {
        if (status == UserStatus.LOCKED) return false;
        if (lockedUntil == null) return true;
        return lockedUntil.isBefore(Instant.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
