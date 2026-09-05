package com.kmbank.modules.transaction.service;

import com.kmbank.modules.transaction.entity.Transaction;
import com.kmbank.modules.transaction.enums.TransactionStatus;
import com.kmbank.modules.transaction.repository.LedgerEntryRepository;
import com.kmbank.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Executes the reconciliation logic for a single stuck PENDING transaction.
 *
 * <p>Extracted from {@link ReconciliationService} as a dedicated bean so that
 * {@code @Transactional} is applied through the Spring proxy rather than via
 * {@code this.}-style self-invocation (which Spring's proxy-based AOP silently
 * ignores). This mirrors the same pattern used by {@link PendingTransactionService}.</p>
 *
 * <p>Each call to {@link #reconcileOne} runs in its own transaction, ensuring
 * that a failure on one row does not affect the others.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationExecutorService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Value("${kmbank.reconciliation.pending-threshold-minutes:15}")
    private int pendingThresholdMinutes;

    /**
     * Reconciles a single stuck PENDING transaction.
     *
     * <p>Re-fetches the transaction inside this transaction boundary to eliminate
     * the race window between the bulk query in {@link ReconciliationService} and
     * now: if a legitimate in-flight operation completed in the meantime, the row's
     * status will no longer be PENDING and this method is a no-op.</p>
     *
     * <h4>Decision logic</h4>
     * <ul>
     *   <li>Ledger entries exist: the money moved but the status update was lost.
     *       Mark {@code COMPLETED}.</li>
     *   <li>No ledger entries: the ledger engine never ran. No money moved.
     *       Mark {@code FAILED}.</li>
     * </ul>
     *
     * @param txn the stale-read Transaction from the bulk query;
     *            only the ID is used — the row is re-fetched inside this transaction
     * @return the final resolved status ({@code COMPLETED} or {@code FAILED}),
     *         or {@code null} if the transaction was no longer PENDING and was skipped
     */
    @Transactional
    public TransactionStatus reconcileOne(Transaction txn) {
        Transaction fresh = transactionRepository.findById(txn.getId()).orElse(null);
        if (fresh == null || fresh.getStatus() != TransactionStatus.PENDING) {
            log.debug("Skipping txnId={}: status is now {} (no longer PENDING)",
                    txn.getId(), fresh != null ? fresh.getStatus() : "not found");
            return null;
        }

        boolean hasLedgerEntries = ledgerEntryRepository.existsByTransactionId(fresh.getId());

        if (hasLedgerEntries) {
            fresh.setStatus(TransactionStatus.COMPLETED);
            fresh.setCompletedAt(Instant.now());
            fresh.setUpdatedAt(Instant.now());
            transactionRepository.save(fresh);
            log.warn("Reconciled txnId={} (ref={}) -> COMPLETED (ledger entries found)",
                    fresh.getId(), fresh.getReferenceNumber());
            return TransactionStatus.COMPLETED;
        } else {
            fresh.setStatus(TransactionStatus.FAILED);
            fresh.setFailedAt(Instant.now());
            fresh.setUpdatedAt(Instant.now());
            fresh.setDescription("Reconciled as FAILED: transaction stuck in PENDING state "
                    + "with no ledger entries after " + pendingThresholdMinutes + " minutes");
            transactionRepository.save(fresh);
            log.warn("Reconciled txnId={} (ref={}) -> FAILED (no ledger entries found)",
                    fresh.getId(), fresh.getReferenceNumber());
            return TransactionStatus.FAILED;
        }
    }
}
