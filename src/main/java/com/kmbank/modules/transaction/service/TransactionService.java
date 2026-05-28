package com.kmbank.modules.transaction.service;

import com.kmbank.modules.account.entity.BankAccount;
import com.kmbank.modules.account.repository.BankAccountRepository;
import com.kmbank.modules.customer.repository.CustomerRepository;
import com.kmbank.modules.transaction.dto.response.RecentTransactionResponse;
import com.kmbank.modules.transaction.entity.Transaction;
import com.kmbank.modules.transaction.mapper.TransactionMapper;
import com.kmbank.modules.transaction.repository.TransactionRepository;
import com.kmbank.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final CustomerRepository customerRepository;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public List<RecentTransactionResponse> getRecentTransactions(CustomUserPrincipal principal, int limit) {
        int cappedLimit = Math.min(limit, 10);
        UUID userId = principal.getId();

        var customerOpt = customerRepository.findByUserId(userId);
        if (customerOpt.isEmpty()) {
            log.debug("No customer profile found for userId={}", userId);
            return Collections.emptyList();
        }

        var accounts = bankAccountRepository.findByCustomerId(customerOpt.get().getId());
        if (accounts.isEmpty()) {
            log.debug("No bank accounts found for customerId={}", customerOpt.get().getId());
            return Collections.emptyList();
        }

        Set<UUID> accountIds = accounts.stream()
                .map(BankAccount::getId)
                .collect(Collectors.toSet());

        List<Transaction> transactions = transactionRepository.findRecentByAccountIds(
                accountIds, PageRequest.of(0, cappedLimit));

        return transactions.stream()
                .map(tx -> transactionMapper.toRecentResponse(tx, accountIds))
                .collect(Collectors.toList());
    }
}
