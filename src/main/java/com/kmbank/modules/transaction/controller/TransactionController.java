package com.kmbank.modules.transaction.controller;

import com.kmbank.common.dto.ApiResponse;
import com.kmbank.modules.transaction.dto.response.PaginatedTransactionResponse;
import com.kmbank.modules.transaction.service.TransactionService;
import com.kmbank.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Returns a paginated list of COMPLETED transactions for the given account.
     * The authenticated user must own the account.
     *
     * @param accountId the UUID of the account whose history is requested
     * @param page      1-based page number (default: 1)
     * @param limit     number of records per page (default: 20, max: 100)
     * @param principal the authenticated user principal
     * @return paginated transaction response
     */
    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<ApiResponse<PaginatedTransactionResponse>> getTransactionHistory(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        log.info("REST request to GET /api/v1/accounts/{}/transactions for userId={}, page={}, limit={}",
                accountId, principal.getId(), page, limit);

        PaginatedTransactionResponse response =
                transactionService.getTransactionHistory(principal.getId(), accountId, page, limit);

        log.info("Transaction history retrieved: accountId={}, userId={}, total={}",
                accountId, principal.getId(), response.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction history retrieved successfully"));
    }
}
