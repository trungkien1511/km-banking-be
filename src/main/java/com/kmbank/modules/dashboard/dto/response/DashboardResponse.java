package com.kmbank.modules.dashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.transaction.dto.response.TransactionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({ "totalBalance", "currency", "accounts", "recentTransactions" })
public class DashboardResponse {

    /**
     * Sum of {@code availableBalance} across all ACTIVE and FROZEN accounts.
     * Closed accounts are excluded from the total.
     */
    private BigDecimal totalBalance;

    /**
     * Currency for {@link #totalBalance} — always "VND" for this implementation.
     */
    private String currency;

    /**
     * All bank accounts belonging to the authenticated user,
     * including FROZEN and CLOSED (displayed with their status).
     */
    private List<AccountResponse> accounts;

    /**
     * Up to 10 most recent COMPLETED transactions across all user accounts,
     * sorted by {@code createdAt} descending.
     */
    private List<TransactionResponse> recentTransactions;
}
