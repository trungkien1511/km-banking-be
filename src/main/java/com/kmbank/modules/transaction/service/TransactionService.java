package com.kmbank.modules.transaction.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.account.entity.BankAccount;
import com.kmbank.modules.account.repository.BankAccountRepository;
import com.kmbank.modules.account.service.AccountService;
import com.kmbank.modules.transaction.service.LedgerService;
import com.kmbank.modules.transaction.dto.request.DepositRequest;
import com.kmbank.modules.transaction.dto.request.TransferRequest;
import com.kmbank.modules.transaction.dto.request.WithdrawalRequest;
import com.kmbank.modules.transaction.dto.response.PaginatedTransactionResponse;
import com.kmbank.modules.transaction.dto.response.TransactionResponse;
import com.kmbank.modules.transaction.entity.Transaction;
import com.kmbank.modules.transaction.enums.TransactionDirection;
import com.kmbank.modules.transaction.enums.TransactionStatus;
import com.kmbank.modules.transaction.enums.TransactionType;
import com.kmbank.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for transaction read operations used by the Dashboard and Account
 * History features.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final BankAccountRepository bankAccountRepository;

    @Value("${kmbank.system-account-number:SYSTEM-000}")
    private String systemAccountNumber;

    /**
     * Returns all PENDING transactions where any of the given accounts is involved
     * (source or destination), with the direction calculated relative to those
     * accounts.
     *
     * @param accountIds the account UUIDs to filter on
     * @return list of transaction DTOs with direction
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getPendingTransactions(Collection<UUID> accountIds) {
        List<UUID> ids = accountIds instanceof List ? (List<UUID>) accountIds : List.copyOf(accountIds);

        return transactionRepository.findPendingByAccountIds(ids).stream()
                .filter(txn -> txn.getStatus() == TransactionStatus.PENDING)
                .map(txn -> {
                    TransactionDirection dir = calculateDirection(txn, accountIds);
                    UUID viewerAccountId = resolveViewerAccount(txn, accountIds);
                    return buildResponse(txn, dir, viewerAccountId);
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns COMPLETED transactions (up to {@code limit} most recent) where any of
     * the
     * given accounts is involved, with direction calculated relative to those
     * accounts.
     *
     * @param accountIds the account UUIDs to filter on
     * @param limit      maximum number of transactions to return
     * @return list of transaction DTOs with direction, newest first
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getCompletedTransactions(Collection<UUID> accountIds, int limit) {
        List<UUID> ids = accountIds instanceof List ? (List<UUID>) accountIds : List.copyOf(accountIds);

        return transactionRepository.findCompletedByAccountIds(ids).stream()
                .limit(limit)
                .map(txn -> {
                    TransactionDirection dir = calculateDirection(txn, accountIds);
                    UUID viewerAccountId = resolveViewerAccount(txn, accountIds);
                    return buildResponse(txn, dir, viewerAccountId);
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns paginated COMPLETED transaction history for a single account,
     * after validating that the given user owns the account.
     *
     * <p>
     * Pagination is 1-indexed (page 1 = first page).
     *
     * @param userId    the authenticated user's UUID
     * @param accountId the bank account UUID whose history is requested
     * @param page      1-based page number (must be ≥ 1)
     * @param limit     page size (must be between 1 and 100 inclusive)
     * @return paginated response including content and pagination metadata
     * @throws BusinessException with {@link ErrorCode#ACCESS_DENIED} if the user
     *                           does not own the account
     * @throws BusinessException with {@link ErrorCode#INVALID_PAGE} if page &lt; 1
     * @throws BusinessException with {@link ErrorCode#INVALID_LIMIT} if limit is
     *                           not in [1, 100]
     */
    @Transactional(readOnly = true)
    public PaginatedTransactionResponse getTransactionHistory(UUID userId, UUID accountId, int page, int limit) {
        // Validate page
        if (page < 1) {
            throw new BusinessException("Page number must be at least 1", ErrorCode.INVALID_PAGE);
        }

        // Validate limit
        if (limit < 1 || limit > 100) {
            throw new BusinessException("Limit must be between 1 and 100", ErrorCode.INVALID_LIMIT);
        }

        // Validate account ownership
        if (!accountService.isAccountOwner(userId, accountId)) {
            throw new BusinessException("Access denied: account does not belong to the user",
                    ErrorCode.ACCESS_DENIED);
        }

        // Convert 1-indexed page to 0-indexed for Spring Data
        Pageable pageable = PageRequest.of(page - 1, limit);

        Page<Transaction> txnPage = transactionRepository.findCompletedByAccountId(accountId, pageable);

        // Map each transaction to a DTO with direction relative to this account
        Page<TransactionResponse> responsePage = txnPage.map(txn -> {
            TransactionDirection dir = calculateDirection(txn, List.of(accountId));
            return buildResponse(txn, dir, accountId);
        });

        return PaginatedTransactionResponse.from(responsePage);
    }

    /**
     * Saves a Transaction with PENDING status in a separate transaction context.
     * This ensures the record persists even if the outer ledger operation rolls
     * back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction savePendingTransaction(UUID sourceAccountId, UUID destinationAccountId,
            UUID initiatedBy, BigDecimal amount,
            TransactionType type, String description) {
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
                .build();
        return transactionRepository.save(txn);
    }

    /**
     * Marks a Transaction as FAILED with the given reason, in a separate
     * transaction context.
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

    private String generateReferenceNumber() {
        return "TXN-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Executes an internal transfer between two bank accounts.
     *
     * @param request the transfer details
     * @param userId  the authenticated user's UUID
     * @return the completed transaction response
     */
    @Transactional
    public TransactionResponse transfer(TransferRequest request, UUID userId) {
        log.info("Processing transfer: userId={}, sourceAccountId={}, destAccNum={}, amount={}",
                userId, request.getSourceAccountId(), request.getDestinationAccountNumber(), request.getAmount());

        // Validate ownership
        if (!accountService.isAccountOwner(userId, request.getSourceAccountId())) {
            throw new BusinessException("Access denied: you do not own the source account", ErrorCode.ACCESS_DENIED);
        }

        // Resolve destination account
        BankAccount destAccount = bankAccountRepository.findByAccountNumber(request.getDestinationAccountNumber())
                .orElseThrow(() -> new BusinessException("Destination account not found", ErrorCode.ACCOUNT_NOT_FOUND));

        // Prevent same-account transfers
        if (request.getSourceAccountId().equals(destAccount.getId())) {
            throw new BusinessException("Cannot transfer to the same account", ErrorCode.TRANSFER_SAME_ACCOUNT);
        }

        // Save PENDING transaction (commits immediately)
        Transaction txn = savePendingTransaction(
                request.getSourceAccountId(), destAccount.getId(), userId,
                request.getAmount(), TransactionType.TRANSFER, request.getDescription());

        try {
            ledgerService.executeTransfer(
                    request.getSourceAccountId(), destAccount.getId(),
                    request.getAmount(), txn.getId());

            // Mark COMPLETED
            txn.setStatus(TransactionStatus.COMPLETED);
            txn.setCompletedAt(Instant.now());
            txn.setUpdatedAt(Instant.now());
            transactionRepository.save(txn);

            log.info("Transfer completed: txnId={}, ref={}", txn.getId(), txn.getReferenceNumber());
            return TransactionResponse.fromEntity(txn, request.getSourceAccountId());

        } catch (Exception ex) {
            log.error("Transfer failed: txnId={}, reason={}", txn.getId(), ex.getMessage());
            markTransactionFailed(txn.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Executes a mock deposit by transferring from the System Account to the user's
     * account.
     */
    @Transactional
    public TransactionResponse deposit(DepositRequest request, UUID userId) {
        log.info("Processing deposit: userId={}, accountId={}, amount={}",
                userId, request.getAccountId(), request.getAmount());

        if (!accountService.isAccountOwner(userId, request.getAccountId())) {
            throw new BusinessException("Access denied: you do not own this account", ErrorCode.ACCESS_DENIED);
        }

        BankAccount systemAccount = bankAccountRepository.findByAccountNumber(systemAccountNumber)
                .orElseThrow(() -> new BusinessException("System account not found", ErrorCode.INTERNAL_SERVER_ERROR));

        String desc = request.getDescription() != null ? request.getDescription() : "Deposit";

        Transaction txn = savePendingTransaction(
                systemAccount.getId(), request.getAccountId(), userId,
                request.getAmount(), TransactionType.DEPOSIT, desc);

        try {
            ledgerService.executeTransfer(
                    systemAccount.getId(), request.getAccountId(),
                    request.getAmount(), txn.getId());

            txn.setStatus(TransactionStatus.COMPLETED);
            txn.setCompletedAt(Instant.now());
            txn.setUpdatedAt(Instant.now());
            transactionRepository.save(txn);

            log.info("Deposit completed: txnId={}, ref={}", txn.getId(), txn.getReferenceNumber());
            return TransactionResponse.fromEntity(txn, request.getAccountId());

        } catch (Exception ex) {
            log.error("Deposit failed: txnId={}, reason={}", txn.getId(), ex.getMessage());
            markTransactionFailed(txn.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Executes a mock withdrawal by transferring from the user's account to the
     * System Account.
     */
    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request, UUID userId) {
        log.info("Processing withdrawal: userId={}, accountId={}, amount={}",
                userId, request.getAccountId(), request.getAmount());

        if (!accountService.isAccountOwner(userId, request.getAccountId())) {
            throw new BusinessException("Access denied: you do not own this account", ErrorCode.ACCESS_DENIED);
        }

        BankAccount systemAccount = bankAccountRepository.findByAccountNumber(systemAccountNumber)
                .orElseThrow(() -> new BusinessException("System account not found", ErrorCode.INTERNAL_SERVER_ERROR));

        String desc = request.getDescription() != null ? request.getDescription() : "Withdrawal";

        Transaction txn = savePendingTransaction(
                request.getAccountId(), systemAccount.getId(), userId,
                request.getAmount(), TransactionType.WITHDRAWAL, desc);

        try {
            ledgerService.executeTransfer(
                    request.getAccountId(), systemAccount.getId(),
                    request.getAmount(), txn.getId());

            txn.setStatus(TransactionStatus.COMPLETED);
            txn.setCompletedAt(Instant.now());
            txn.setUpdatedAt(Instant.now());
            transactionRepository.save(txn);

            log.info("Withdrawal completed: txnId={}, ref={}", txn.getId(), txn.getReferenceNumber());
            return TransactionResponse.fromEntity(txn, request.getAccountId());

        } catch (Exception ex) {
            log.error("Withdrawal failed: txnId={}, reason={}", txn.getId(), ex.getMessage());
            markTransactionFailed(txn.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Calculates the transaction direction from the perspective of the given
     * account set.
     *
     * <ul>
     * <li>Returns {@link TransactionDirection#OUT} if the source account is in
     * {@code userAccountIds}.</li>
     * <li>Returns {@link TransactionDirection#IN} if the destination account is in
     * {@code userAccountIds}.</li>
     * <li>Defaults to {@link TransactionDirection#IN} if neither condition matches
     * (e.g. deposit with no source).</li>
     * </ul>
     *
     * @param txn            the transaction entity
     * @param userAccountIds the set of account UUIDs belonging to the user/viewer
     * @return the computed direction
     */
    public TransactionDirection calculateDirection(Transaction txn, Collection<UUID> userAccountIds) {
        if (txn.getSourceAccountId() != null && userAccountIds.contains(txn.getSourceAccountId())) {
            return TransactionDirection.OUT;
        }
        if (txn.getDestinationAccountId() != null && userAccountIds.contains(txn.getDestinationAccountId())) {
            return TransactionDirection.IN;
        }
        // Fallback: treat as incoming (e.g. deposit with no source)
        return TransactionDirection.IN;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the "viewer" account UUID from the given set — the account that is
     * directly involved in the transaction and belongs to the user.
     */
    private UUID resolveViewerAccount(Transaction txn, Collection<UUID> accountIds) {
        if (txn.getSourceAccountId() != null && accountIds.contains(txn.getSourceAccountId())) {
            return txn.getSourceAccountId();
        }
        if (txn.getDestinationAccountId() != null && accountIds.contains(txn.getDestinationAccountId())) {
            return txn.getDestinationAccountId();
        }
        return null;
    }

    /**
     * Builds a {@link TransactionResponse} DTO from a transaction entity and
     * computed direction.
     */
    private TransactionResponse buildResponse(Transaction txn, TransactionDirection direction, UUID viewerAccountId) {
        TransactionResponse response = TransactionResponse.fromEntity(txn, viewerAccountId);
        // Override direction with our enum-based calculation
        response.setDirection(direction != null ? direction.name() : null);
        return response;
    }
}
