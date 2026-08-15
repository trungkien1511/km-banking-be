package com.kmbank.modules.transaction.controller;

import com.kmbank.common.dto.ApiResponse;
import com.kmbank.modules.transaction.dto.request.DepositRequest;
import com.kmbank.modules.transaction.dto.request.TransferRequest;
import com.kmbank.modules.transaction.dto.request.WithdrawalRequest;
import com.kmbank.modules.transaction.dto.response.PaginatedTransactionResponse;
import com.kmbank.modules.transaction.dto.response.TransactionResponse;
import com.kmbank.modules.transaction.service.TransactionService;
import com.kmbank.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

                PaginatedTransactionResponse response = transactionService.getTransactionHistory(principal.getId(),
                                accountId, page, limit);

                log.info("Transaction history retrieved: accountId={}, userId={}, total={}",
                                accountId, principal.getId(), response.getTotalElements());
                return ResponseEntity.ok(ApiResponse.success(response, "Transaction history retrieved successfully"));
        }

        /**
         * Executes an internal transfer between two bank accounts.
         */
        @PostMapping("/transactions/transfer")
        public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
                        @Valid @RequestBody TransferRequest request,
                        @AuthenticationPrincipal CustomUserPrincipal principal) {

                log.info("REST request to POST /api/v1/accounts/transactions/transfer for userId={}",
                                principal.getId());

                TransactionResponse response = transactionService.transfer(request, principal.getId());
                return ResponseEntity.ok(ApiResponse.success(response, "Transfer completed successfully"));
        }

        /**
         * Executes a mock deposit into a bank account (for testing).
         */
        @PostMapping("/transactions/deposit")
        public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
                        @Valid @RequestBody DepositRequest request,
                        @AuthenticationPrincipal CustomUserPrincipal principal) {

                log.info("REST request to POST /api/v1/accounts/transactions/deposit for userId={}",
                                principal.getId());

                TransactionResponse response = transactionService.deposit(request, principal.getId());
                return ResponseEntity.ok(ApiResponse.success(response, "Deposit completed successfully"));
        }

        /**
         * Executes a mock withdrawal from a bank account (for testing).
         */
        @PostMapping("/transactions/withdrawal")
        public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
                        @Valid @RequestBody WithdrawalRequest request,
                        @AuthenticationPrincipal CustomUserPrincipal principal) {

                log.info("REST request to POST /api/v1/accounts/transactions/withdrawal for userId={}",
                                principal.getId());

                TransactionResponse response = transactionService.withdraw(request, principal.getId());
                return ResponseEntity.ok(ApiResponse.success(response, "Withdrawal completed successfully"));
        }
}
