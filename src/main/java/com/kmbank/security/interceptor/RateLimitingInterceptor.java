package com.kmbank.security.interceptor;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.security.CustomUserPrincipal;
import com.kmbank.security.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.security.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }

        if (rateLimit == null) {
            return true;
        }

        int maxRequests = rateLimit.requestsPerMinute();
        String clientKey = resolveClientKey(request);

        long currentMinute = System.currentTimeMillis() / 60_000L;
        String redisKey = "rate_limit:" + clientKey + ":" + currentMinute;

        try {
            Long currentCount = stringRedisTemplate.opsForValue().increment(redisKey);
            if (currentCount != null && currentCount == 1) {
                stringRedisTemplate.expire(redisKey, Duration.ofSeconds(60));
            }

            if (currentCount != null && currentCount > maxRequests) {
                log.warn("Rate limit exceeded for client={}: {} requests in current minute (limit={})",
                        clientKey, currentCount, maxRequests);
                throw new BusinessException("Rate limit exceeded. Please try again later.", ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            log.warn("Redis unavailable for rate limiting, allowing request (fail-open): {}", ex.getMessage());
            return true;
        }

        return true;
    }

    /**
     * Resolves the client identification key for rate limiting.
     *
     * <p>Authenticated users are identified by "user:{id}".
     * Unauthenticated requests fall back to "ip:{address}".</p>
     *
     * <p>NOTE: Enable {@code app.security.trust-proxy-headers=true} ONLY if the application
     * is deployed behind a trusted reverse proxy or load balancer (e.g. Nginx, AWS ALB)
     * that strips/overwrites client-supplied X-Forwarded-For headers. When false (default),
     * request.getRemoteAddr() is used directly to prevent IP spoofing attacks.</p>
     */
    private String resolveClientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal principal) {
            return "user:" + principal.getId();
        }

        if (trustProxyHeaders) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return "ip:" + xForwardedFor.split(",")[0].trim();
            }
        }

        return "ip:" + request.getRemoteAddr();
    }
}
