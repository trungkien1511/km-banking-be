package com.kmbank.modules.transaction.repository;

import com.kmbank.modules.transaction.entity.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccountId IN :accountIds OR t.destinationAccountId IN :accountIds ORDER BY t.initiatedAt DESC")
    List<Transaction> findRecentByAccountIds(@Param("accountIds") Set<UUID> accountIds, Pageable pageable);
}
