package com.kmbank.modules.account.controller;

import com.kmbank.common.dto.ApiResponse;
import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.account.service.AccountService;
import com.kmbank.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Returns the details of a specific bank account owned by the authenticated user.
     *
     * @param accountId the UUID of the account to retrieve
     * @param principal the authenticated user principal
     * @return account detail DTO
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountDetail(
            @PathVariable UUID accountId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        log.info("REST request to GET /api/v1/accounts/{} for userId={}", accountId, principal.getId());

        AccountResponse response = accountService.getAccountDetail(principal.getId(), accountId);

        log.info("Account detail retrieved successfully: accountId={}, userId={}", accountId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Account retrieved successfully"));
    }
}
