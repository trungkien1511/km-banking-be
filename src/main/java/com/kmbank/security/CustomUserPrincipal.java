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

    /**
     * The UUID of the {@code Customer} profile linked to this user.
     * May be {@code null} if the user has no customer profile (e.g. admin/staff).
     */
    private final UUID customerId;

    public CustomUserPrincipal(User user) {
        this(user, null);
    }

    public CustomUserPrincipal(User user, UUID customerId) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.status = user.getStatus();
        this.lockedUntil = user.getLockedUntil();
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        this.user = user;
        this.customerId = customerId;
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

    @Override
    public boolean isAccountNonLocked() {
        if (status == UserStatus.LOCKED)
            return false;
        if (lockedUntil == null)
            return true;
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
