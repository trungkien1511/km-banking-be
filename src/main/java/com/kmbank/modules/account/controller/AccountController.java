package com.kmbank.modules.account.controller;

import com.kmbank.common.dto.ApiResponse;
import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.account.dto.response.RecipientLookupDto;
import com.kmbank.modules.account.service.AccountService;
import com.kmbank.security.CustomUserPrincipal;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@Validated
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

    /**
     * Looks up a recipient account by account number for transfer pre-verification.
     *
     * <p>Returns the partially masked account holder name and account status so the
     * frontend can show the user who they are sending to before they submit the transfer.
     * CLOSED accounts are excluded. The caller must be authenticated.
     *
     * @param accountNumber the exact account number to look up
     * @param principal     the authenticated user principal (required; not used for lookup, only for auth)
     * @return masked recipient info: accountNumber, accountHolderName, status
     */
    @GetMapping("/lookup-recipient")
    public ResponseEntity<ApiResponse<RecipientLookupDto>> lookupRecipient(
            @RequestParam @NotBlank String accountNumber,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        log.info("REST request to GET /api/v1/accounts/lookup-recipient for userId={}, accountNumber={}",
                principal.getId(), accountNumber);

        RecipientLookupDto response = accountService.lookupRecipient(accountNumber);

        log.info("Recipient lookup resolved: accountNumber={}, status={}", accountNumber, response.status());
        return ResponseEntity.ok(ApiResponse.success(response, "Recipient resolved successfully"));
    }
}
