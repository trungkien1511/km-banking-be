package com.kmbank.modules.transaction.repository;

import com.kmbank.modules.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

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
}
