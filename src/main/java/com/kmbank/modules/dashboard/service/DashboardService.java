package com.kmbank.modules.dashboard.service;

import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.account.service.AccountService;
import com.kmbank.modules.dashboard.dto.response.DashboardResponse;
import com.kmbank.modules.transaction.dto.response.TransactionResponse;
import com.kmbank.modules.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates all data required for the customer dashboard screen.
 * <p>
 * Aggregates accounts, balances, pending and recent completed transactions
 * into a single {@link DashboardResponse} for the given customer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AccountService accountService;
    private final TransactionService transactionService;

    /**
     * Builds the full dashboard response for the given customer.
     *
     * <ol>
     *   <li>Fetches all accounts for the customer.</li>
     *   <li>Calculates the total available balance across ACTIVE accounts.</li>
     *   <li>Extracts account IDs from the accounts list.</li>
     *   <li>Fetches PENDING transactions across those accounts.</li>
     *   <li>Fetches the 10 most recent COMPLETED transactions across those accounts.</li>
     * </ol>
     *
     * @param customerId the UUID of the authenticated customer
     * @return a fully populated {@link DashboardResponse}
     */
    public DashboardResponse getDashboard(UUID customerId) {
        log.debug("Building dashboard for customerId={}", customerId);

        // 1. Fetch all accounts for the customer
        List<AccountResponse> accounts = accountService.getAccountsByCustomer(customerId);

        // 2. Fetch total available balance (ACTIVE accounts only)
        BigDecimal totalBalance = accountService.getTotalAvailableBalance(customerId);

        // 3. Extract account IDs for transaction queries
        List<UUID> accountIds = accounts.stream()
                .map(AccountResponse::getId)
                .toList();

        // 4. Fetch PENDING transactions (no PROCESSING — only PENDING status)
        List<TransactionResponse> pendingTransactions =
                transactionService.getPendingTransactions(accountIds);

        // 5. Fetch last 10 COMPLETED transactions
        List<TransactionResponse> recentTransactions =
                transactionService.getCompletedTransactions(accountIds, 10);

        log.debug("Dashboard built for customerId={}: accounts={}, pending={}, recent={}",
                customerId, accounts.size(), pendingTransactions.size(), recentTransactions.size());

        return DashboardResponse.builder()
                .totalBalance(totalBalance)
                .currency("VND")
                .accounts(accounts)
                .recentTransactions(recentTransactions)
                .build();
    }
}
