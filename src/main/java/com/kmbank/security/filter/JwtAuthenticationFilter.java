package com.kmbank.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmbank.common.dto.ApiResponse;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.security.jwt.JwtService;
import com.kmbank.security.CustomUserDetailsService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = 7;

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper; // Dùng để chuyển ApiResponse thành JSON chuẩn

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 1. Bỏ qua OPTIONS request (CORS preflight) để tránh làm nghẽn luồng CORS
        // Config
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // 2. Nếu không có Token -> Cho đi tiếp (Spring Security sẽ chặn ở
        // AuthorizationFilter nếu endpoint yêu cầu bảo mật)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(BEARER_PREFIX_LENGTH);

        try {
            final UUID userId = jwtService.extractUserId(jwt);

            if (userId == null) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                        "Invalid token: missing user ID");
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // 3. Kiểm tra tính hợp lệ của Token trước khi load DB (Tiết kiệm hiệu năng)
                if (!jwtService.isAccessTokenValid(jwt, userId)) {
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                            "Token expired or invalid");
                    return;
                }

                UserDetails userDetails = userDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Authenticated user: {} with roles: {}", userId, userDetails.getAuthorities());
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired for request: {}", request.getRequestURI());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Token expired");

        } catch (MalformedJwtException | SignatureException e) {
            log.warn("Invalid JWT token structure/signature: {} - {}", request.getRequestURI(), e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
                    "Invalid token signature");

        } catch (Exception e) {
            log.error("Unexpected error during JWT authentication for request: {}", request.getRequestURI(), e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR,
                    "Authentication error");
        }
    }

    /**
     * Gửi phản hồi lỗi chuẩn hóa theo đúng cấu trúc ApiResponse của dự án
     */
    private void sendErrorResponse(HttpServletResponse response, int httpStatus, ErrorCode errorCode, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Tạo ra đúng cấu trúc bọc ApiResponse chuẩn doanh nghiệp của bạn
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .code(errorCode.name())
                .message(message)
                .build();

        // Convert Object sang JSON String và xuất ra Response
        String json = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(json);
    }
}
