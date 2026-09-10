package com.kmbank.modules.account.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.account.dto.response.AccountResponse;
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
}
