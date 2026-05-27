package com.kmbank.security.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HttpRequestUtils {

    /**
     * Lấy IP thực của client (xử lý cả khi qua proxy)
     */
    public String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "Unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }

    /**
     * Lấy User-Agent từ request
     */
    public String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "Unknown";
        }
        return request.getHeader("User-Agent");
    }

    /**
     * Lấy tên thiết bị đơn giản từ User-Agent
     */
    public String getDeviceName(HttpServletRequest request) {
        String userAgent = getUserAgent(request);
        if (userAgent == null) {
            return "Unknown";
        }
        
        userAgent = userAgent.toLowerCase();
        
        if (userAgent.contains("mobile")) return "Mobile";
        if (userAgent.contains("windows")) return "Windows PC";
        if (userAgent.contains("mac")) return "Mac";
        if (userAgent.contains("linux")) return "Linux";
        if (userAgent.contains("iphone")) return "iPhone";
        if (userAgent.contains("ipad")) return "iPad";
        if (userAgent.contains("android")) return "Android";
        
        return "Unknown Device";
    }
}
