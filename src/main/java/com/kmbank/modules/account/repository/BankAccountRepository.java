package com.kmbank.modules.account.repository;

import com.kmbank.modules.account.dto.response.RecipientLookupDto;
import com.kmbank.modules.account.entity.BankAccount;
import com.kmbank.modules.account.enums.AccountStatus;
import com.kmbank.modules.customer.entity.Customer;
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

    /**
     * Checks if a bank account is owned by the customer associated with the given user ID in a single query.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM BankAccount a
            JOIN Customer c ON c.id = a.customerId
            WHERE c.userId = :userId AND a.id = :accountId
            """)
    boolean isOwnedByUser(@Param("userId") UUID userId, @Param("accountId") UUID accountId);

    /**
     * Looks up minimal recipient information for a given account number.
     *
     * <p>Joins BankAccount → Customer → User to resolve the account holder's full name.
     * Returns an empty Optional if the account number does not exist.
     * Intentionally excludes CLOSED accounts from results — attempting to transfer
     * to a closed account would fail at the ledger level anyway.
     *
     * <p>The caller (AccountService) is responsible for applying name masking before
     * returning the DTO to the client.
     *
     * @param accountNumber the account number to look up
     * @return an Optional containing the lookup result, or empty if not found
     */
    @Query("""
            SELECT new com.kmbank.modules.account.dto.response.RecipientLookupDto(
                a.accountNumber,
                u.fullName,
                CAST(a.status AS string)
            )
            FROM BankAccount a
            JOIN Customer c ON c.id = a.customerId
            JOIN User u ON u.id = c.userId
            WHERE a.accountNumber = :accountNumber
              AND a.status <> com.kmbank.modules.account.enums.AccountStatus.CLOSED
            """)
    Optional<RecipientLookupDto> findRecipientByAccountNumber(@Param("accountNumber") String accountNumber);
}
