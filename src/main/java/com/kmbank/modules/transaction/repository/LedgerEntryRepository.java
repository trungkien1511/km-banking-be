package com.kmbank.modules.transaction.repository;

import com.kmbank.modules.transaction.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Checks whether any ledger entries exist for the given transaction.
     * Used by the reconciliation scheduler to determine whether a stuck PENDING
     * transaction was partially or fully processed by the ledger engine.
     *
     * @param transactionId the parent transaction UUID
     * @return {@code true} if at least one ledger entry is linked to this transaction
     */
    boolean existsByTransactionId(UUID transactionId);
}
