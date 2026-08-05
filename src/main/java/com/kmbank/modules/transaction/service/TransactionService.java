package com.kmbank.modules.transaction.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.account.service.AccountService;
import com.kmbank.modules.transaction.dto.response.PaginatedTransactionResponse;
import com.kmbank.modules.transaction.dto.response.TransactionResponse;
import com.kmbank.modules.transaction.entity.Transaction;
import com.kmbank.modules.transaction.enums.TransactionDirection;
import com.kmbank.modules.transaction.enums.TransactionStatus;
import com.kmbank.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for transaction read operations used by the Dashboard and Account History features.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    /**
     * Returns all PENDING transactions where any of the given accounts is involved
     * (source or destination), with the direction calculated relative to those accounts.
     *
     * @param accountIds the account UUIDs to filter on
     * @return list of transaction DTOs with direction
     */
    public List<TransactionResponse> getPendingTransactions(Collection<UUID> accountIds) {
        List<UUID> ids = accountIds instanceof List ? (List<UUID>) accountIds : List.copyOf(accountIds);

        return transactionRepository.findPendingByAccountIds(ids).stream()
                .filter(txn -> txn.getStatus() == TransactionStatus.PENDING)
                .map(txn -> {
                    TransactionDirection dir = calculateDirection(txn, accountIds);
                    UUID viewerAccountId = resolveViewerAccount(txn, accountIds);
                    return buildResponse(txn, dir, viewerAccountId);
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns COMPLETED transactions (up to {@code limit} most recent) where any of the
     * given accounts is involved, with direction calculated relative to those accounts.
     *
     * @param accountIds the account UUIDs to filter on
     * @param limit      maximum number of transactions to return
     * @return list of transaction DTOs with direction, newest first
     */
    public List<TransactionResponse> getCompletedTransactions(Collection<UUID> accountIds, int limit) {
        List<UUID> ids = accountIds instanceof List ? (List<UUID>) accountIds : List.copyOf(accountIds);

        return transactionRepository.findCompletedByAccountIds(ids).stream()
                .limit(limit)
                .map(txn -> {
                    TransactionDirection dir = calculateDirection(txn, accountIds);
                    UUID viewerAccountId = resolveViewerAccount(txn, accountIds);
                    return buildResponse(txn, dir, viewerAccountId);
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns paginated COMPLETED transaction history for a single account,
     * after validating that the given user owns the account.
     *
     * <p>Pagination is 1-indexed (page 1 = first page).
     *
     * @param userId    the authenticated user's UUID
     * @param accountId the bank account UUID whose history is requested
     * @param page      1-based page number (must be ≥ 1)
     * @param limit     page size (must be between 1 and 100 inclusive)
     * @return paginated response including content and pagination metadata
     * @throws BusinessException with {@link ErrorCode#ACCESS_DENIED} if the user does not own the account
     * @throws BusinessException with {@link ErrorCode#INVALID_PAGE} if page &lt; 1
     * @throws BusinessException with {@link ErrorCode#INVALID_LIMIT} if limit is not in [1, 100]
     */
    public PaginatedTransactionResponse getTransactionHistory(UUID userId, UUID accountId, int page, int limit) {
        // Validate page
        if (page < 1) {
            throw new BusinessException("Page number must be at least 1", ErrorCode.INVALID_PAGE);
        }

        // Validate limit
        if (limit < 1 || limit > 100) {
            throw new BusinessException("Limit must be between 1 and 100", ErrorCode.INVALID_LIMIT);
        }

        // Validate account ownership
        if (!accountService.isAccountOwner(userId, accountId)) {
            throw new BusinessException("Access denied: account does not belong to the user",
                    ErrorCode.ACCESS_DENIED);
        }

        // Convert 1-indexed page to 0-indexed for Spring Data
        Pageable pageable = PageRequest.of(page - 1, limit);

        Page<Transaction> txnPage = transactionRepository.findCompletedByAccountId(accountId, pageable);

        // Map each transaction to a DTO with direction relative to this account
        Page<TransactionResponse> responsePage = txnPage.map(txn -> {
            TransactionDirection dir = calculateDirection(txn, List.of(accountId));
            return buildResponse(txn, dir, accountId);
        });

        return PaginatedTransactionResponse.from(responsePage);
    }

    /**
     * Calculates the transaction direction from the perspective of the given account set.
     *
     * <ul>
     *   <li>Returns {@link TransactionDirection#OUT} if the source account is in {@code userAccountIds}.</li>
     *   <li>Returns {@link TransactionDirection#IN} if the destination account is in {@code userAccountIds}.</li>
     *   <li>Defaults to {@link TransactionDirection#IN} if neither condition matches (e.g. deposit with no source).</li>
     * </ul>
     *
     * @param txn            the transaction entity
     * @param userAccountIds the set of account UUIDs belonging to the user/viewer
     * @return the computed direction
     */
    public TransactionDirection calculateDirection(Transaction txn, Collection<UUID> userAccountIds) {
        if (txn.getSourceAccountId() != null && userAccountIds.contains(txn.getSourceAccountId())) {
            return TransactionDirection.OUT;
        }
        if (txn.getDestinationAccountId() != null && userAccountIds.contains(txn.getDestinationAccountId())) {
            return TransactionDirection.IN;
        }
        // Fallback: treat as incoming (e.g. deposit with no source)
        return TransactionDirection.IN;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the "viewer" account UUID from the given set — the account that is
     * directly involved in the transaction and belongs to the user.
     */
    private UUID resolveViewerAccount(Transaction txn, Collection<UUID> accountIds) {
        if (txn.getSourceAccountId() != null && accountIds.contains(txn.getSourceAccountId())) {
            return txn.getSourceAccountId();
        }
        if (txn.getDestinationAccountId() != null && accountIds.contains(txn.getDestinationAccountId())) {
            return txn.getDestinationAccountId();
        }
        return null;
    }

    /**
     * Builds a {@link TransactionResponse} DTO from a transaction entity and computed direction.
     */
    private TransactionResponse buildResponse(Transaction txn, TransactionDirection direction, UUID viewerAccountId) {
        TransactionResponse response = TransactionResponse.fromEntity(txn, viewerAccountId);
        // Override direction with our enum-based calculation
        response.setDirection(direction != null ? direction.name() : null);
        return response;
    }
}
