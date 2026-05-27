package com.kmbank.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;

    @PostConstruct
    public void init() {
        if (jwtProperties.getSecret() == null || jwtProperties.getSecret().length() < 32) {
            log.error("JWT secret is too short! Must be at least 32 characters");
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters) for HS256");
        }
    }

    public UUID extractUserId(String token) {
        if (isTokenInvalid(token)) {
            return null;
        }
        String subject = extractClaim(token, Claims::getSubject);
        return subject != null ? UUID.fromString(subject) : null;
    }

    public String extractUsername(String token) {
        if (isTokenInvalid(token)) {
            return null;
        }
        return extractClaim(token, claims -> claims.get(CLAIM_USERNAME, String.class));
    }

    public String extractRole(String token) {
        if (isTokenInvalid(token)) {
            return null;
        }
        return extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateAccessToken(UUID userId, String username, String role) {
        log.info("Generating token for user: {} with role: {}", username, role);
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put(CLAIM_USERNAME, username);
        extraClaims.put(CLAIM_ROLE, role);
        return buildToken(extraClaims, userId, getExpirationMs());
    }

    public String generateRefreshToken(UUID userId) {
        long refreshExpirationMs = jwtProperties.getRefreshExpirationDays() * 24 * 60 * 60 * 1000;
        log.info("Generating refresh token for user: {}", userId);
        return buildToken(new HashMap<>(), userId, refreshExpirationMs);
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UUID userId,
            long expirationMs) {
        long nowMillis = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userId.toString())
                .issuedAt(new Date(nowMillis))
                .expiration(new Date(nowMillis + expirationMs))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean isAccessTokenValid(String token, UUID expectedUserId) {
        if (isTokenInvalid(token)) {
            return false;
        }
        try {
            final UUID extractedId = extractUserId(token);
            return extractedId != null && extractedId.equals(expectedUserId) && !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.warn("Token expired for user: {}", expectedUserId);
            return false;
        } catch (MalformedJwtException | SignatureException e) {
            log.warn("Invalid token signature or format for user: {}", expectedUserId);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error validating token for user: {}", expectedUserId, e);
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Refresh token is null or empty");
            return false;
        }

        try {
            return !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.warn("Refresh token expired");
            return false;
        } catch (Exception e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private boolean isTokenInvalid(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Token is null or empty");
            return true;
        }
        return false;
    }

    public long getExpirationMs() {
        return jwtProperties.getExpirationSeconds() * 1000;
    }
}
