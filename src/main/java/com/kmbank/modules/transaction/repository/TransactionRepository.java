package com.kmbank.modules.transaction.repository;

import com.kmbank.modules.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Find a transaction by its client-supplied idempotency key.
     * Used to detect duplicate requests before creating a new PENDING record.
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Atomically transitions a transaction from FAILED to PENDING.
     *
     * <p>The {@code WHERE t.status = 'FAILED'} guard makes this a compare-and-swap:
     * only one concurrent caller can win the race. The method returns the number of
     * rows updated — exactly {@code 1} on success, {@code 0} if the row no longer has
     * status FAILED (meaning another thread already reactivated it or it was
     * completed/cancelled in the meantime). Callers must treat a {@code 0} return as
     * a concurrency conflict and surface it as a 409.</p>
     *
     * @param id the UUID of the FAILED transaction to reactivate
     * @return 1 if the row was updated, 0 if the WHERE condition did not match
     */
    @Modifying
    @Query("""
            UPDATE Transaction t
            SET t.status = com.kmbank.modules.transaction.enums.TransactionStatus.PENDING,
                t.failedAt = null,
                t.description = null,
                t.updatedAt = CURRENT_TIMESTAMP
            WHERE t.id = :id AND t.status = com.kmbank.modules.transaction.enums.TransactionStatus.FAILED
            """)
    int reactivateIfFailed(@Param("id") UUID id);

    /**
     * Find all PENDING transactions where the given account is either source or destination.
     * Used to check for in-flight transactions before processing a new transfer.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.status = 'PENDING'
              AND (t.sourceAccountId IN :accountIds OR t.destinationAccountId IN :accountIds)
            """)
    List<Transaction> findPendingByAccountIds(@Param("accountIds") List<UUID> accountIds);

    /**
     * Find all COMPLETED transactions where the given account is either source or destination.
     * Useful for bulk history retrieval across multiple accounts.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.status = 'COMPLETED'
              AND (t.sourceAccountId IN :accountIds OR t.destinationAccountId IN :accountIds)
            ORDER BY t.createdAt DESC
            """)
    List<Transaction> findCompletedByAccountIds(@Param("accountIds") List<UUID> accountIds);

    /**
     * Find COMPLETED transactions for a single account with pagination.
     * Account can appear as source or destination.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.status = 'COMPLETED'
              AND (t.sourceAccountId = :accountId OR t.destinationAccountId = :accountId)
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findCompletedByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    /**
     * Count COMPLETED transactions for a single account.
     * Account can appear as source or destination.
     */
    @Query("""
            SELECT COUNT(t) FROM Transaction t
            WHERE t.status = 'COMPLETED'
              AND (t.sourceAccountId = :accountId OR t.destinationAccountId = :accountId)
            """)
    long countCompletedByAccountId(@Param("accountId") UUID accountId);

    /**
     * Find PENDING transactions older than the given threshold.
     * Used by the reconciliation scheduler to detect stuck transactions.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.status = 'PENDING'
              AND t.createdAt < :threshold
            """)
    List<Transaction> findPendingOlderThan(@Param("threshold") Instant threshold);
}
