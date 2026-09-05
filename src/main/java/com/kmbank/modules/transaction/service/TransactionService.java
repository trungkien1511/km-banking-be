package com.kmbank.modules.transaction.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.account.entity.BankAccount;
import com.kmbank.modules.account.repository.BankAccountRepository;
import com.kmbank.modules.account.service.AccountService;
import com.kmbank.modules.transaction.dto.request.DepositRequest;
import com.kmbank.modules.transaction.dto.request.TransferRequest;
import com.kmbank.modules.transaction.dto.request.WithdrawalRequest;
import com.kmbank.modules.transaction.dto.response.PaginatedTransactionResponse;
import com.kmbank.modules.transaction.dto.response.TransactionResponse;
import com.kmbank.modules.transaction.entity.Transaction;
import com.kmbank.modules.transaction.enums.TransactionDirection;
import com.kmbank.modules.transaction.enums.TransactionStatus;
import com.kmbank.modules.transaction.enums.TransactionType;
import com.kmbank.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for transaction read and write operations.
 *
 * <h3>Proxy ordering: Retry wraps Transaction</h3>
 * <p>{@code @Retryable} and {@code @Transactional} both work through Spring AOP proxies.
 * The order matters: the Retry proxy <em>must</em> be the outermost wrapper so it can
 * intercept the {@link ObjectOptimisticLockingFailureException} that Hibernate throws
 * during commit (which occurs as the Transaction proxy's commit hook runs, outside the
 * method body). If Transaction were outermost, the exception would escape the Retry
 * proxy entirely and retries would never fire.</p>
 *
 * <p>Spring Retry uses {@code @Order(Ordered.LOWEST_PRECEDENCE - 1)} (i.e. {@code 2147483646})
 * internally, and Spring Transaction uses {@code @Order(Ordered.LOWEST_PRECEDENCE)}
 * ({@code 2147483647}) by default — meaning Retry is already applied first (outer) and
 * Transaction is inner. This is the correct and desired ordering.</p>
 *
 * <p>The {@code @Order(1)} on this class is an <em>explicit, documented guarantee</em>
 * that ensures this bean's proxy chain is processed at the correct priority should the
 * defaults ever change in a future Spring version upgrade.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Order(1)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final BankAccountRepository bankAccountRepository;
    private final PendingTransactionService pendingTransactionService;

    @Value("${kmbank.system-account-number:SYSTEM-000}")
    private String systemAccountNumber;

    // =========================================================================
    // Read operations
    // =========================================================================

    /**
     * Returns all PENDING transactions where any of the given accounts is involved
     * (source or destination), with the direction calculated relative to those accounts.
     */
    @Transactional(readOnly = true)
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
     * Returns COMPLETED transactions (up to {@code limit} most recent) where any of
     * the given accounts is involved, with direction calculated relative to those accounts.
     */
    @Transactional(readOnly = true)
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
     */
    @Transactional(readOnly = true)
    public PaginatedTransactionResponse getTransactionHistory(UUID userId, UUID accountId, int page, int limit) {
        if (page < 1) {
            throw new BusinessException("Page number must be at least 1", ErrorCode.INVALID_PAGE);
        }
        if (limit < 1 || limit > 100) {
            throw new BusinessException("Limit must be between 1 and 100", ErrorCode.INVALID_LIMIT);
        }
        if (!accountService.isAccountOwner(userId, accountId)) {
            throw new BusinessException("Access denied: account does not belong to the user",
                    ErrorCode.ACCESS_DENIED);
        }

        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<Transaction> txnPage = transactionRepository.findCompletedByAccountId(accountId, pageable);

        Page<TransactionResponse> responsePage = txnPage.map(txn -> {
            TransactionDirection dir = calculateDirection(txn, List.of(accountId));
            return buildResponse(txn, dir, accountId);
        });

        return PaginatedTransactionResponse.from(responsePage);
    }

    // =========================================================================
    // Write operations
    // =========================================================================

    /**
     * Executes an internal transfer between two bank accounts.
     *
     * <h4>Retry + Transaction proxy ordering</h4>
     * <p>Spring Retry's advisor runs at {@code Ordered.LOWEST_PRECEDENCE - 1} and
     * Spring Transaction runs at {@code Ordered.LOWEST_PRECEDENCE}, so Retry is
     * always the outer proxy. The {@code @Retryable} here (not on
     * {@link LedgerService#executeTransfer}) ensures the entire unit-of-work is
     * retried as a fresh attempt.</p>
     *
     * <h4>Idempotency + retry interaction</h4>
     * <p>On a retry caused by {@link ObjectOptimisticLockingFailureException}:</p>
     * <ol>
     *   <li>Attempt N saved a PENDING row with {@code idempotency_key = K}.</li>
     *   <li>The ledger threw; that row became FAILED.</li>
     *   <li>{@code @Retryable} restarts this method.</li>
     *   <li>{@code checkIdempotency(K)} finds the FAILED row and returns its ID
     *       (not null, not a cached response — a sentinel "reuse this row").</li>
     *   <li>{@code pendingTransactionService.reusePendingTransaction(id)} resets the
     *       row to PENDING in its own {@code REQUIRES_NEW} transaction, reusing the
     *       same {@code id} and {@code reference_number} to keep audit trail intact.</li>
     *   <li>The ledger runs again with the existing transaction ID.</li>
     * </ol>
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional
    public TransactionResponse transfer(TransferRequest request, UUID userId) {
        log.info("Processing transfer: userId={}, sourceAccountId={}, destAccNum={}, amount={}",
                userId, request.getSourceAccountId(), request.getDestinationAccountNumber(), request.getAmount());

        // --- Idempotency check ---
        Transaction txn;
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            IdempotencyResult result = resolveIdempotency(request.getIdempotencyKey(), request.getSourceAccountId(), userId);
            if (result.cachedResponse != null) {
                return result.cachedResponse;
            }
            if (result.existingFailedId != null) {
                // Retry path: reuse the FAILED row rather than inserting a new one
                txn = pendingTransactionService.reusePendingTransaction(result.existingFailedId);
            } else {
                // Normal path
                if (!accountService.isAccountOwner(userId, request.getSourceAccountId())) {
                    throw new BusinessException("Access denied: you do not own the source account",
                            ErrorCode.ACCESS_DENIED);
                }
                BankAccount destAccount = bankAccountRepository
                        .findByAccountNumber(request.getDestinationAccountNumber())
                        .orElseThrow(() -> new BusinessException("Destination account not found",
                                ErrorCode.ACCOUNT_NOT_FOUND));
                if (request.getSourceAccountId().equals(destAccount.getId())) {
                    throw new BusinessException("Cannot transfer to the same account",
                            ErrorCode.TRANSFER_SAME_ACCOUNT);
                }
                txn = pendingTransactionService.savePendingTransaction(
                        request.getSourceAccountId(), destAccount.getId(), userId,
                        request.getAmount(), TransactionType.TRANSFER, request.getDescription(),
                        request.getIdempotencyKey());
            }
        } else {
            // No idempotency key — standard path
            if (!accountService.isAccountOwner(userId, request.getSourceAccountId())) {
                throw new BusinessException("Access denied: you do not own the source account",
                        ErrorCode.ACCESS_DENIED);
            }
            BankAccount destAccount = bankAccountRepository
                    .findByAccountNumber(request.getDestinationAccountNumber())
                    .orElseThrow(() -> new BusinessException("Destination account not found",
                            ErrorCode.ACCOUNT_NOT_FOUND));
            if (request.getSourceAccountId().equals(destAccount.getId())) {
                throw new BusinessException("Cannot transfer to the same account", ErrorCode.TRANSFER_SAME_ACCOUNT);
            }
            txn = pendingTransactionService.savePendingTransaction(
                    request.getSourceAccountId(), destAccount.getId(), userId,
                    request.getAmount(), TransactionType.TRANSFER, request.getDescription(), null);
        }

        try {
            ledgerService.executeTransfer(
                    txn.getSourceAccountId(), txn.getDestinationAccountId(),
                    txn.getAmount(), txn.getId());

            txn.setStatus(TransactionStatus.COMPLETED);
            txn.setCompletedAt(Instant.now());
            txn.setUpdatedAt(Instant.now());
            transactionRepository.save(txn);

            log.info("Transfer completed: txnId={}, ref={}", txn.getId(), txn.getReferenceNumber());
            return TransactionResponse.fromEntity(txn, request.getSourceAccountId());

        } catch (Exception ex) {
            log.error("Transfer failed: txnId={}, reason={}", txn.getId(), ex.getMessage());
            pendingTransactionService.markTransactionFailed(txn.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Executes a mock deposit by transferring from the System Account to the user's account.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional
    public TransactionResponse deposit(DepositRequest request, UUID userId) {
        log.info("Processing deposit: userId={}, accountId={}, amount={}",
                userId, request.getAccountId(), request.getAmount());

        if (!accountService.isAccountOwner(userId, request.getAccountId())) {
            throw new BusinessException("Access denied: you do not own this account", ErrorCode.ACCESS_DENIED);
        }

        BankAccount systemAccount = bankAccountRepository.findByAccountNumber(systemAccountNumber)
                .orElseThrow(() -> new BusinessException("System account not found", ErrorCode.INTERNAL_SERVER_ERROR));

        String desc = request.getDescription() != null ? request.getDescription() : "Deposit";

        Transaction txn;
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            IdempotencyResult result = resolveIdempotency(request.getIdempotencyKey(), request.getAccountId(), userId);
            if (result.cachedResponse != null) {
                return result.cachedResponse;
            }
            txn = (result.existingFailedId != null)
                    ? pendingTransactionService.reusePendingTransaction(result.existingFailedId)
                    : pendingTransactionService.savePendingTransaction(
                            systemAccount.getId(), request.getAccountId(), userId,
                            request.getAmount(), TransactionType.DEPOSIT, desc,
                            request.getIdempotencyKey());
        } else {
            txn = pendingTransactionService.savePendingTransaction(
                    systemAccount.getId(), request.getAccountId(), userId,
                    request.getAmount(), TransactionType.DEPOSIT, desc, null);
        }

        try {
            ledgerService.executeTransfer(
                    txn.getSourceAccountId(), txn.getDestinationAccountId(),
                    txn.getAmount(), txn.getId());

            txn.setStatus(TransactionStatus.COMPLETED);
            txn.setCompletedAt(Instant.now());
            txn.setUpdatedAt(Instant.now());
            transactionRepository.save(txn);

            log.info("Deposit completed: txnId={}, ref={}", txn.getId(), txn.getReferenceNumber());
            return TransactionResponse.fromEntity(txn, request.getAccountId());

        } catch (Exception ex) {
            log.error("Deposit failed: txnId={}, reason={}", txn.getId(), ex.getMessage());
            pendingTransactionService.markTransactionFailed(txn.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Executes a mock withdrawal by transferring from the user's account to the System Account.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request, UUID userId) {
        log.info("Processing withdrawal: userId={}, accountId={}, amount={}",
                userId, request.getAccountId(), request.getAmount());

        if (!accountService.isAccountOwner(userId, request.getAccountId())) {
            throw new BusinessException("Access denied: you do not own this account", ErrorCode.ACCESS_DENIED);
        }

        BankAccount systemAccount = bankAccountRepository.findByAccountNumber(systemAccountNumber)
                .orElseThrow(() -> new BusinessException("System account not found", ErrorCode.INTERNAL_SERVER_ERROR));

        String desc = request.getDescription() != null ? request.getDescription() : "Withdrawal";

        Transaction txn;
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            IdempotencyResult result = resolveIdempotency(request.getIdempotencyKey(), request.getAccountId(), userId);
            if (result.cachedResponse != null) {
                return result.cachedResponse;
            }
            txn = (result.existingFailedId != null)
                    ? pendingTransactionService.reusePendingTransaction(result.existingFailedId)
                    : pendingTransactionService.savePendingTransaction(
                            request.getAccountId(), systemAccount.getId(), userId,
                            request.getAmount(), TransactionType.WITHDRAWAL, desc,
                            request.getIdempotencyKey());
        } else {
            txn = pendingTransactionService.savePendingTransaction(
                    request.getAccountId(), systemAccount.getId(), userId,
                    request.getAmount(), TransactionType.WITHDRAWAL, desc, null);
        }

        try {
            ledgerService.executeTransfer(
                    txn.getSourceAccountId(), txn.getDestinationAccountId(),
                    txn.getAmount(), txn.getId());

            txn.setStatus(TransactionStatus.COMPLETED);
            txn.setCompletedAt(Instant.now());
            txn.setUpdatedAt(Instant.now());
            transactionRepository.save(txn);

            log.info("Withdrawal completed: txnId={}, ref={}", txn.getId(), txn.getReferenceNumber());
            return TransactionResponse.fromEntity(txn, request.getAccountId());

        } catch (Exception ex) {
            log.error("Withdrawal failed: txnId={}, reason={}", txn.getId(), ex.getMessage());
            pendingTransactionService.markTransactionFailed(txn.getId(), ex.getMessage());
            throw ex;
        }
    }

    // =========================================================================
    // Direction helpers
    // =========================================================================

    /**
     * Calculates the transaction direction from the perspective of the given account set.
     */
    public TransactionDirection calculateDirection(Transaction txn, Collection<UUID> userAccountIds) {
        if (txn.getSourceAccountId() != null && userAccountIds.contains(txn.getSourceAccountId())) {
            return TransactionDirection.OUT;
        }
        if (txn.getDestinationAccountId() != null && userAccountIds.contains(txn.getDestinationAccountId())) {
            return TransactionDirection.IN;
        }
        return TransactionDirection.IN;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Result holder for idempotency resolution.
     *
     * <ul>
     *   <li>{@code cachedResponse != null} — a COMPLETED transaction exists; return it directly.</li>
     *   <li>{@code existingFailedId != null} — a FAILED transaction exists with this key;
     *       call {@link PendingTransactionService#reusePendingTransaction} to reset it.</li>
     *   <li>Both null — no existing transaction; proceed normally.</li>
     * </ul>
     */
    private record IdempotencyResult(TransactionResponse cachedResponse, UUID existingFailedId) {
        static IdempotencyResult proceed() {
            return new IdempotencyResult(null, null);
        }
        static IdempotencyResult cached(TransactionResponse r) {
            return new IdempotencyResult(r, null);
        }
        static IdempotencyResult reuse(UUID id) {
            return new IdempotencyResult(null, id);
        }
    }

    /**
     * Resolves the idempotency state for a given key.
     *
     * <ul>
     *   <li>COMPLETED row found → return cached response.</li>
     *   <li>PENDING row found → throw 409 (another request is in flight).</li>
     *   <li>FAILED row found → return the row's ID so the caller can reuse it
     *       (reset to PENDING) instead of creating a new row that would hit the
     *       unique constraint.</li>
     *   <li>No row → proceed normally.</li>
     * </ul>
     *
     * <p><b>Ownership check:</b> the key is validated against {@code userId} before
     * any status branch is evaluated. If the row was created by a different user, we
     * throw a 409 — the same response a duplicate request receives — so we neither
     * leak the existence of another user's transaction nor allow cross-user reactivation
     * that could redirect money movement.</p>
     *
     * @param idempotencyKey  the client-supplied key
     * @param viewerAccountId account UUID used for direction calculation in the cached response
     * @param userId          the authenticated user's UUID; must match the row's {@code initiatedBy}
     * @return an {@link IdempotencyResult} describing the action to take
     * @throws BusinessException with {@link ErrorCode#DUPLICATE_TRANSACTION} if status is PENDING
     *                           or if the key belongs to a different user
     */
    private IdempotencyResult resolveIdempotency(String idempotencyKey, UUID viewerAccountId, UUID userId) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    // Ownership guard — checked before any status branch so we never expose
                    // another user's transaction state (existence, status, or amount).
                    if (!existing.getInitiatedBy().equals(userId)) {
                        throw new BusinessException(
                                "Idempotency key conflict: key belongs to a different user",
                                ErrorCode.DUPLICATE_TRANSACTION);
                    }
                    switch (existing.getStatus()) {
                        case COMPLETED -> {
                            log.info("Idempotent response: key={}, txnId={}", idempotencyKey, existing.getId());
                            return IdempotencyResult.cached(
                                    TransactionResponse.fromEntity(existing, viewerAccountId));
                        }
                        case PENDING -> throw new BusinessException(
                                "A transaction with idempotency key '" + idempotencyKey
                                        + "' is already in progress",
                                ErrorCode.DUPLICATE_TRANSACTION);
                        case FAILED, CANCELLED -> {
                            // Retry path: signal to reuse this row instead of inserting a new one.
                            log.info("Idempotency reuse: key={}, failedTxnId={}", idempotencyKey, existing.getId());
                            return IdempotencyResult.reuse(existing.getId());
                        }
                        default -> {
                            return IdempotencyResult.proceed();
                        }
                    }
                })
                .orElse(IdempotencyResult.proceed());
    }

    /**
     * Resolves the "viewer" account UUID — the account directly involved in the
     * transaction that belongs to the user.
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
     * Builds a {@link TransactionResponse} DTO with the computed direction overridden.
     */
    private TransactionResponse buildResponse(Transaction txn, TransactionDirection direction, UUID viewerAccountId) {
        TransactionResponse response = TransactionResponse.fromEntity(txn, viewerAccountId);
        response.setDirection(direction != null ? direction.name() : null);
        return response;
    }
}
