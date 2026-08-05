package com.kmbank.modules.dashboard.controller;

import com.kmbank.common.dto.ApiResponse;
import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.dashboard.dto.response.DashboardResponse;
import com.kmbank.modules.dashboard.service.DashboardService;
import com.kmbank.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Returns the full dashboard data for the authenticated customer,
     * including accounts, total balance, and recent transactions.
     *
     * @param principal the authenticated user principal
     * @return dashboard response DTO
     */
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        log.info("REST request to GET /api/v1/dashboard for userId={}", principal.getId());

        if (principal.getCustomerId() == null) {
            log.warn("No customer profile found for userId={}", principal.getId());
            throw new BusinessException("No customer profile associated with this account", ErrorCode.FORBIDDEN);
        }

        DashboardResponse response = dashboardService.getDashboard(principal.getCustomerId());

        log.info("Dashboard retrieved successfully for customerId={}", principal.getCustomerId());
        return ResponseEntity.ok(ApiResponse.success(response, "Dashboard retrieved successfully"));
    }
}
