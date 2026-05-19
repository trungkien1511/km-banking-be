package com.kmbank.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    @PostConstruct
    public void init() {
        if (jwtProperties.getSecret() == null || jwtProperties.getSecret().length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters) for HS256");
        }
    }

    public UUID extractUserId(String token) {
        String subject = extractClaim(token, Claims::getSubject);
        return subject != null ? UUID.fromString(subject) : null;
    }

    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UUID userId, String username, String role) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("username", username);
        extraClaims.put("role", role);
        return buildToken(extraClaims, userId, jwtProperties.getExpirationSeconds() * 1000);
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

    public boolean isTokenValid(String token, UUID expectedUserId) {
        final UUID extractedId = extractUserId(token);
        return extractedId != null && extractedId.equals(expectedUserId) && !isTokenExpired(token);
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
}
