package com.kmbank.modules.transaction.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.transaction.entity.Transaction;
import com.kmbank.modules.transaction.enums.TransactionStatus;
import com.kmbank.modules.transaction.enums.TransactionType;
import com.kmbank.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * Handles the lifecycle of a Transaction record in isolated transaction contexts.
 *
 * <p>Extracted from {@link TransactionService} so that {@code REQUIRES_NEW}
 * propagation works correctly through the Spring proxy (self-invocation within
 * the same bean bypasses the proxy and silently ignores the annotation).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingTransactionService {

    private final TransactionRepository transactionRepository;

    /**
     * Saves a new Transaction with PENDING status in its own, independent DB
     * transaction. This ensures the row is visible to callers even if the outer
     * ledger transaction rolls back.
     *
     * @param sourceAccountId      nullable for deposits
     * @param destinationAccountId nullable for withdrawals
     * @param initiatedBy          the user who initiated the operation
     * @param amount               transfer amount (> 0)
     * @param type                 TRANSFER / DEPOSIT / WITHDRAWAL
     * @param description          optional human-readable description
     * @param idempotencyKey       optional client-supplied idempotency key (UUID string)
     * @return the persisted {@link Transaction}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction savePendingTransaction(UUID sourceAccountId, UUID destinationAccountId,
            UUID initiatedBy, BigDecimal amount,
            TransactionType type, String description,
            String idempotencyKey) {
        Transaction txn = Transaction.builder()
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .initiatedBy(initiatedBy)
                .amount(amount)
                .transactionType(type)
                .status(TransactionStatus.PENDING)
                .referenceNumber(generateReferenceNumber())
                .description(description)
                .valueDate(LocalDate.now())
                .idempotencyKey(idempotencyKey)
                .build();
        return transactionRepository.save(txn);
    }

    /**
     * Atomically resets an existing FAILED transaction back to PENDING status for retry.
     *
     * <p><b>Why this exists:</b> when {@code @Retryable} fires on an
     * {@code OptimisticLockException}, the flow is:
     * <ol>
     *   <li>Attempt 1 saves a PENDING row with {@code idempotency_key = K}.</li>
     *   <li>The ledger throws; {@code markTransactionFailed} sets the row to FAILED.</li>
     *   <li>{@code @Retryable} restarts the method from the top.</li>
     *   <li>{@code resolveIdempotency(K)} finds the FAILED row and returns its ID.</li>
     *   <li>Without this method, {@code savePendingTransaction} would INSERT a new row
     *       with the same key, hitting the UNIQUE constraint.</li>
     * </ol>
     *
     * <p><b>Concurrency guard:</b> uses a conditional UPDATE
     * ({@code WHERE status = 'FAILED'}) as a compare-and-swap. If two concurrent
     * requests both see status FAILED and both call this method, only one DB UPDATE
     * will match — the other gets {@code rowsUpdated = 0} and receives a 409.
     * This prevents the double-spend scenario where both callers proceed to
     * {@code executeTransfer} with the same {@code transaction_id}.</p>
     *
     * @param existingTxnId the ID of the FAILED transaction to reuse
     * @return the same {@link Transaction} entity, now in PENDING status
     * @throws BusinessException with {@link ErrorCode#DUPLICATE_TRANSACTION} if another
     *                           concurrent request already won the reactivation race
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction reusePendingTransaction(UUID existingTxnId) {
        int updated = transactionRepository.reactivateIfFailed(existingTxnId);
        if (updated == 0) {
            // Another concurrent request already reactivated (or completed) this row.
            throw new BusinessException(
                    "This transaction is already being processed by another request",
                    ErrorCode.DUPLICATE_TRANSACTION);
        }
        return transactionRepository.findById(existingTxnId)
                .orElseThrow(() -> new BusinessException("Transaction not found", ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * Marks a Transaction as FAILED in its own, independent DB transaction so
     * the failure record persists even if the caller's transaction rolls back.
     *
     * @param transactionId the UUID of the transaction to fail
     * @param reason        human-readable failure reason (stored in description)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markTransactionFailed(UUID transactionId, String reason) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException("Transaction not found", ErrorCode.RESOURCE_NOT_FOUND));
        txn.setStatus(TransactionStatus.FAILED);
        txn.setDescription(reason);
        txn.setFailedAt(Instant.now());
        txn.setUpdatedAt(Instant.now());
        transactionRepository.save(txn);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String generateReferenceNumber() {
        return "TXN-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
