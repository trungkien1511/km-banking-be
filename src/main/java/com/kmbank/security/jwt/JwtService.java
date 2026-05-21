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

// #1: Thêm @Slf4j vào đầu class
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    // #5: Constants cho claim keys - tránh hardcode string trực tiếp
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;

    @PostConstruct
    public void init() {
        if (jwtProperties.getSecret() == null || jwtProperties.getSecret().length() < 32) {
            // #2: Thêm log.error() trước khi throw để ghi nhận lỗi config
            log.error("JWT secret is too short! Must be at least 32 characters");
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters) for HS256");
        }
    }

    /**
     * Trích xuất User ID (UUID) từ JWT token.
     *
     * @param token JWT token cần phân tích
     * @return UUID của user, hoặc null nếu token không hợp lệ
     */
    // #3: Kiểm tra null/blank trước khi xử lý
    public UUID extractUserId(String token) {
        if (isTokenInvalid(token)) {
            return null;
        }
        String subject = extractClaim(token, Claims::getSubject);
        return subject != null ? UUID.fromString(subject) : null;
    }

    /**
     * Trích xuất username từ JWT token.
     *
     * @param token JWT token cần phân tích
     * @return username, hoặc null nếu token không hợp lệ
     */
    public String extractUsername(String token) {
        if (isTokenInvalid(token)) {
            return null;
        }
        // #5: Dùng constant thay vì chuỗi trực tiếp
        return extractClaim(token, claims -> claims.get(CLAIM_USERNAME, String.class));
    }

    /**
     * Trích xuất role từ JWT token.
     *
     * @param token JWT token cần phân tích
     * @return role, hoặc null nếu token không hợp lệ
     */
    public String extractRole(String token) {
        if (isTokenInvalid(token)) {
            return null;
        }
        // #5: Dùng constant thay vì chuỗi trực tiếp
        return extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
    }

    /**
     * Phương thức generic để trích xuất bất kỳ claim nào từ token.
     *
     * @param token          JWT token cần phân tích
     * @param claimsResolver hàm lambda để lấy claim cụ thể
     * @param <T>            kiểu dữ liệu của claim
     * @return giá trị của claim
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Sinh JWT access token cho người dùng đã xác thực.
     *
     * @param userId   UUID của user
     * @param username username của user
     * @param role     role của user
     * @return chuỗi JWT token
     */
    public String generateToken(UUID userId, String username, String role) {
        // #7: Log khi bắt đầu sinh token
        log.info("Generating token for user: {} with role: {}", username, role);
        Map<String, Object> extraClaims = new HashMap<>();
        // #5: Dùng constant thay vì chuỗi trực tiếp
        extraClaims.put(CLAIM_USERNAME, username);
        extraClaims.put(CLAIM_ROLE, role);
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

    /**
     * Kiểm tra tính hợp lệ của JWT token.
     *
     * @param token          JWT token cần kiểm tra
     * @param expectedUserId User ID mong đợi
     * @return true nếu token hợp lệ, false nếu không
     */
    // #4: Bọc try-catch để bắt các loại exception từ thư viện jjwt
    public boolean isTokenValid(String token, UUID expectedUserId) {
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

    /**
     * Helper: Kiểm tra xem token có bị null hoặc rỗng không.
     *
     * @param token JWT token cần kiểm tra
     * @return true nếu token không hợp lệ (null hoặc blank)
     */
    // #8: Helper method tái sử dụng để tránh lặp code null-check
    private boolean isTokenInvalid(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Token is null or empty");
            return true;
        }
        return false;
    }
}
