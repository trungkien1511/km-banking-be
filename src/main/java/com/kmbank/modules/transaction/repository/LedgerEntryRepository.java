package com.kmbank.modules.transaction.repository;

import com.kmbank.modules.transaction.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByTransactionId(UUID transactionId);

    /**
     * Counts how many ledger entries exist for the given transaction.
     * In a double-entry system, a fully processed transfer has exactly 2 entries (DEBIT + CREDIT).
     *
     * @param transactionId the parent transaction UUID
     * @return count of ledger entries linked to this transaction
     */
    long countByTransactionId(UUID transactionId);
}
