package com.kmbank.modules.transaction.controller;

import com.kmbank.common.dto.ApiResponse;
import com.kmbank.modules.transaction.dto.response.RecentTransactionResponse;
import com.kmbank.modules.transaction.service.TransactionService;
import com.kmbank.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<RecentTransactionResponse>>> getRecentTransactions(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "3") int limit) {

        log.info("REST request to get recent transactions for userId={}, limit={}", principal.getId(), limit);
        List<RecentTransactionResponse> transactions = transactionService.getRecentTransactions(principal, limit);
        return ResponseEntity.ok(ApiResponse.success(transactions, "Recent transactions retrieved successfully"));
    }
}
