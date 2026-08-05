package com.kmbank.modules.account.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.kmbank.modules.account.entity.BankAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id", "accountNumber", "accountType", "status",
        "balance", "availableBalance", "currency", "createdAt"
})
public class AccountResponse {

    private UUID id;

    private String accountNumber;

    private String accountType;

    private String status;

    private BigDecimal balance;

    private BigDecimal availableBalance;

    private String currency;

    private Instant createdAt;

    /**
     * Maps a {@link BankAccount} entity to an {@link AccountResponse} DTO.
     *
     * @param account the bank account entity
     * @return the response DTO
     */
    public static AccountResponse fromEntity(BankAccount account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType().name())
                .status(account.getStatus().name())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
