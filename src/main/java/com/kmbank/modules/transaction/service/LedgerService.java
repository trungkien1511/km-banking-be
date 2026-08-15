package com.kmbank.modules.transaction.service;

import com.kmbank.common.exception.BusinessException;
import com.kmbank.common.exception.ErrorCode;
import com.kmbank.modules.account.entity.BankAccount;
import com.kmbank.modules.account.enums.AccountStatus;
import com.kmbank.modules.account.repository.BankAccountRepository;
import com.kmbank.modules.transaction.entity.LedgerEntry;
import com.kmbank.modules.transaction.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Core double-entry bookkeeping engine.
 * Every money movement creates exactly two LedgerEntry records (DEBIT + CREDIT)
 * and updates the corresponding BankAccount balances atomically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final BankAccountRepository bankAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Executes a transfer by debiting the sender and crediting the receiver.
     * Both accounts are saved with updated balances and ledger entries are created.
     *
     * @param senderId      UUID of the sending BankAccount
     * @param receiverId    UUID of the receiving BankAccount
     * @param amount        the transfer amount (must be > 0)
     * @param transactionId the parent Transaction ID for linking ledger entries
     * @throws BusinessException with INSUFFICIENT_FUNDS if sender balance < amount
     * @throws BusinessException with ACCOUNT_NOT_FOUND if either account doesn't exist
     * @throws BusinessException with ACCOUNT_INACTIVE if either account is not ACTIVE
     */
    @Transactional
    public void executeTransfer(UUID senderId, UUID receiverId, BigDecimal amount, UUID transactionId) {
        log.debug("Executing ledger transfer: sender={}, receiver={}, amount={}, txnId={}",
                senderId, receiverId, amount, transactionId);

        BankAccount sender = bankAccountRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException("Source account not found", ErrorCode.ACCOUNT_NOT_FOUND));

        BankAccount receiver = bankAccountRepository.findById(receiverId)
                .orElseThrow(() -> new BusinessException("Destination account not found", ErrorCode.ACCOUNT_NOT_FOUND));

        validateAccountActive(sender, "Source");
        validateAccountActive(receiver, "Destination");

        if (sender.getAvailableBalance().compareTo(amount) < 0) {
            log.warn("Insufficient funds: sender={}, available={}, requested={}",
                    senderId, sender.getAvailableBalance(), amount);
            throw new BusinessException("Insufficient funds", ErrorCode.INSUFFICIENT_FUNDS);
        }

        // Debit sender
        BigDecimal senderBalanceBefore = sender.getAvailableBalance();
        sender.setBalance(sender.getBalance().subtract(amount));
        sender.setAvailableBalance(sender.getAvailableBalance().subtract(amount));
        bankAccountRepository.save(sender);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transactionId(transactionId)
                .accountId(senderId)
                .entryType("DEBIT")
                .amount(amount)
                .balanceBefore(senderBalanceBefore)
                .balanceAfter(sender.getAvailableBalance())
                .build();
        ledgerEntryRepository.save(debitEntry);

        // Credit receiver
        BigDecimal receiverBalanceBefore = receiver.getAvailableBalance();
        receiver.setBalance(receiver.getBalance().add(amount));
        receiver.setAvailableBalance(receiver.getAvailableBalance().add(amount));
        bankAccountRepository.save(receiver);

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transactionId(transactionId)
                .accountId(receiverId)
                .entryType("CREDIT")
                .amount(amount)
                .balanceBefore(receiverBalanceBefore)
                .balanceAfter(receiver.getAvailableBalance())
                .build();
        ledgerEntryRepository.save(creditEntry);

        log.info("Ledger transfer completed: txnId={}, sender={} ({}->{}), receiver={} ({}->{})",
                transactionId, senderId, senderBalanceBefore, sender.getAvailableBalance(),
                receiverId, receiverBalanceBefore, receiver.getAvailableBalance());
    }

    private void validateAccountActive(BankAccount account, String label) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(
                    label + " account is not active (status: " + account.getStatus() + ")",
                    ErrorCode.ACCOUNT_INACTIVE);
        }
    }
}
