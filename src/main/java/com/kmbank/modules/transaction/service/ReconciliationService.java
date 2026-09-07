package com.kmbank.modules.transaction.service;

import com.kmbank.modules.transaction.entity.Transaction;
import com.kmbank.modules.transaction.enums.TransactionStatus;
import com.kmbank.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Background reconciliation scheduler that resolves PENDING transactions that were
 * never completed or explicitly failed due to a crash, timeout, or partial failure.
 *
 * <h3>Self-invocation note</h3>
 * <p>The per-row logic is deliberately delegated to {@link ReconciliationExecutorService}
 * rather than a private method on this class. Spring AOP proxies only intercept calls
 * made through the proxy (i.e. from outside the bean). A {@code this.reconcileOne(...)}
 * call inside the same class bypasses the proxy entirely, so any {@code @Transactional}
 * annotation on that method would be silently ignored — the fix applied in
 * {@link PendingTransactionService} applies here too.</p>
 *
 * <h3>Race-condition safety</h3>
 * <p>{@link ReconciliationExecutorService#reconcileOne} re-fetches each transaction
 * inside its own {@code @Transactional} boundary, so a concurrent completion between
 * the bulk query and the per-row update is handled safely.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final TransactionRepository transactionRepository;
    private final ReconciliationExecutorService reconciliationExecutorService;

    /**
     * How many minutes a PENDING transaction must be "stuck" before it is
     * eligible for reconciliation. Defaults to 15 minutes; override via
     * {@code kmbank.reconciliation.pending-threshold-minutes} in application.properties.
     */
    @Value("${kmbank.reconciliation.pending-threshold-minutes:15}")
    private int pendingThresholdMinutes;

    /**
     * Runs every 5 minutes. Finds all PENDING transactions older than
     * {@code pendingThresholdMinutes} and resolves each one via
     * {@link ReconciliationExecutorService#reconcileOne}, which runs in its own
     * transaction so a single failure never aborts the whole batch.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "reconcilePendingTransactions", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void reconcilePendingTransactions() {
        Instant threshold = Instant.now().minus(pendingThresholdMinutes, ChronoUnit.MINUTES);
        List<Transaction> stuckTransactions = transactionRepository.findPendingOlderThan(threshold);

        if (stuckTransactions.isEmpty()) {
            log.debug("Reconciliation run: no stuck PENDING transactions found (threshold={}min)",
                    pendingThresholdMinutes);
            return;
        }

        log.info("Reconciliation run: found {} stuck PENDING transaction(s) older than {} minutes",
                stuckTransactions.size(), pendingThresholdMinutes);

        int completed = 0;
        int failed = 0;
        int skipped = 0;
        int errors = 0;

        for (Transaction txn : stuckTransactions) {
            try {
                // Delegate to the executor bean so @Transactional fires through the proxy
                TransactionStatus resolved = reconciliationExecutorService.reconcileOne(txn);
                if (resolved == null) {
                    skipped++;
                } else if (resolved == TransactionStatus.COMPLETED) {
                    completed++;
                } else {
                    failed++;
                }
            } catch (Exception ex) {
                errors++;
                log.error("Reconciliation error for txnId={}: {}", txn.getId(), ex.getMessage(), ex);
            }
        }

        log.info("Reconciliation complete: completed={}, failed={}, skipped={}, errors={}",
                completed, failed, skipped, errors);
    }
}
