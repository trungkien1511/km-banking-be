package com.kmbank.modules.account.repository;

import com.kmbank.modules.account.entity.BankAccount;
import com.kmbank.modules.account.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByCustomerId(UUID customerId);

    Optional<BankAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Find all accounts belonging to a customer filtered by status.
     */
    @Query("SELECT a FROM BankAccount a WHERE a.customerId = :customerId AND a.status = :status")
    List<BankAccount> findAllByCustomerId(@Param("customerId") UUID customerId,
                                         @Param("status") AccountStatus status);

    /**
     * Sum the available balance across all ACTIVE accounts for a given customer.
     * Returns null if the customer has no active accounts — callers should handle null.
     */
    @Query("SELECT SUM(a.availableBalance) FROM BankAccount a WHERE a.customerId = :customerId AND a.status = 'ACTIVE'")
    BigDecimal sumAvailableBalanceByCustomerId(@Param("customerId") UUID customerId);
}
