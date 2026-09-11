package com.kmbank.modules.account.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.account.dto.response.RecipientLookupDto;
import com.kmbank.modules.account.entity.BankAccount;
import com.kmbank.modules.account.repository.BankAccountRepository;
import com.kmbank.modules.customer.entity.Customer;
import com.kmbank.modules.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service for bank account operations: listing accounts, checking balances,
 * and verifying account ownership.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final BankAccountRepository bankAccountRepository;
    private final CustomerRepository customerRepository;

    /**
     * Returns all bank accounts belonging to the given customer.
     *
     * @param customerId the customer's UUID
     * @return list of account response DTOs (empty list if none found)
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByCustomer(UUID customerId) {
        log.debug("Fetching accounts for customerId={}", customerId);
        List<BankAccount> accounts = bankAccountRepository.findByCustomerId(customerId);
        return accounts.stream()
                .map(AccountResponse::fromEntity)
                .toList();
    }

    /**
     * Returns the sum of available balances across all ACTIVE accounts for a customer.
     * Returns {@link BigDecimal#ZERO} if the customer has no active accounts.
     *
     * @param customerId the customer's UUID
     * @return total available balance (never null)
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalAvailableBalance(UUID customerId) {
        log.debug("Calculating total available balance for customerId={}", customerId);
        BigDecimal total = bankAccountRepository.sumAvailableBalanceByCustomerId(customerId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Returns the detail of a specific bank account, verifying that the requesting
     * user actually owns the account.
     *
     * <p>Ownership check: the account's {@code customerId} must match the {@code id}
     * of the {@link Customer} record whose {@code userId} equals the given {@code userId}.
     *
     * @param userId    the authenticated user's UUID
     * @param accountId the account UUID to retrieve
     * @return the account response DTO
     * @throws BusinessException with {@link ErrorCode#ACCOUNT_NOT_FOUND} if the account does not exist
     * @throws BusinessException with {@link ErrorCode#FORBIDDEN} if the user does not own the account
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountDetail(UUID userId, UUID accountId) {
        log.debug("Fetching account detail for userId={}, accountId={}", userId, accountId);

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.warn("Account not found: accountId={}", accountId);
                    return new BusinessException("Account not found", ErrorCode.ACCOUNT_NOT_FOUND);
                });

        if (!isAccountOwner(userId, accountId)) {
            log.warn("Access denied: userId={} does not own accountId={}", userId, accountId);
            throw new BusinessException("Access denied to account", ErrorCode.FORBIDDEN);
        }

        return AccountResponse.fromEntity(account);
    }

    /**
     * Checks whether the given user owns the given bank account in a single query.
     *
     * @param userId    the authenticated user's UUID
     * @param accountId the account UUID to check
     * @return {@code true} if the user owns the account, {@code false} otherwise
     */
    @Transactional(readOnly = true)
    public boolean isAccountOwner(UUID userId, UUID accountId) {
        log.debug("Checking account ownership: userId={}, accountId={}", userId, accountId);
        return bankAccountRepository.isOwnedByUser(userId, accountId);
    }

    /**
     * Looks up a recipient account by account number and returns masked holder information.
     *
     * <p>Masking rule (Vietnamese banking convention):
     * Given a full name like "NGUYEN VAN ANH", only the first and last tokens are shown in full;
     * all middle tokens are replaced with "**" — result: "NGUYEN ** ANH".
     * Single-token names are returned as-is.
     *
     * <p>CLOSED accounts are excluded at the repository level. FROZEN and INACTIVE accounts
     * are returned with their status so the frontend can show a warning to the user.
     *
     * @param accountNumber the account number to look up (exact match)
     * @return the masked recipient DTO
     * @throws BusinessException with {@link ErrorCode#ACCOUNT_NOT_FOUND} if no matching account exists
     */
    @Transactional(readOnly = true)
    public RecipientLookupDto lookupRecipient(String accountNumber) {
        log.debug("Looking up recipient for accountNumber={}", accountNumber);

        RecipientLookupDto raw = bankAccountRepository
                .findRecipientByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.debug("Recipient lookup found no match for accountNumber={}", accountNumber);
                    return new BusinessException("Account not found", ErrorCode.ACCOUNT_NOT_FOUND);
                });

        return new RecipientLookupDto(
                raw.accountNumber(),
                maskName(raw.accountHolderName()),
                raw.status()
        );
    }

    /**
     * Applies Vietnamese banking name masking.
     *
     * <p>Examples:
     * <ul>
     *   <li>"NGUYEN VAN ANH" → "NGUYEN ** ANH"</li>
     *   <li>"TRAN THI BICH NGOC" → "TRAN ** ** NGOC"</li>
     *   <li>"MADONNA" → "MADONNA" (single token, no masking)</li>
     * </ul>
     */
    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) return fullName;

        String[] parts = fullName.trim().split("\\s+");
        if (parts.length <= 2) return fullName; // nothing to mask

        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length - 1; i++) {
            sb.append(" **");
        }
        sb.append(" ").append(parts[parts.length - 1]);
        return sb.toString();
    }
}
