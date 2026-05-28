package com.kmbank.modules.transaction.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecentTransactionResponse {

    private UUID id;
    private BigDecimal amount;
    private String transactionType;
    private String description;
    private Instant initiatedAt;
}
