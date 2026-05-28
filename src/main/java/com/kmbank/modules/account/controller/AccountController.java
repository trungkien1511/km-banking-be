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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccounts(
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        log.info("REST request to get accounts for userId={}", principal.getId());
        List<AccountResponse> accounts = accountService.getAccountsForCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.success(accounts, "Accounts retrieved successfully"));
    }
}
