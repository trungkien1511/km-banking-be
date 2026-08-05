package com.kmbank.modules.transaction.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.kmbank.modules.transaction.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id", "referenceNumber", "transactionType", "status",
        "amount", "fee", "currency", "direction",
        "sourceAccountId", "destinationAccountId",
        "description", "valueDate", "createdAt"
})
public class TransactionResponse {

    private UUID id;

    private String referenceNumber;

    private String transactionType;

    private String status;

    private BigDecimal amount;

    private BigDecimal fee;

    private String currency;

    /**
     * Direction from the perspective of the requesting user's account:
     * "IN" means money coming in, "OUT" means money going out.
     */
    private String direction;

    private UUID sourceAccountId;

    private UUID destinationAccountId;

    private String description;

    /**
     * Accounting date — used in bank statements and financial reports.
     * May differ from {@link #createdAt} for late-night transactions.
     */
    private LocalDate valueDate;

    /**
     * Actual timestamp the transaction was created in the system.
     */
    private Instant createdAt;

    /**
     * Maps a {@link Transaction} entity to a {@link TransactionResponse} DTO
     * without a direction (direction defaults to null).
     *
     * @param transaction the transaction entity
     * @return the response DTO
     */
    public static TransactionResponse fromEntity(Transaction transaction) {
        return fromEntity(transaction, null);
    }

    /**
     * Maps a {@link Transaction} entity to a {@link TransactionResponse} DTO
     * with an explicit direction ("IN" or "OUT") from the perspective of a
     * given account.
     *
     * @param transaction     the transaction entity
     * @param viewerAccountId the account UUID from whose perspective the
     *                        direction is computed; if null, direction is omitted
     * @return the response DTO
     */
    public static TransactionResponse fromEntity(Transaction transaction, UUID viewerAccountId) {
        String direction = null;
        if (viewerAccountId != null) {
            boolean isDestination = viewerAccountId.equals(transaction.getDestinationAccountId());
            direction = isDestination ? "IN" : "OUT";
        }

        return TransactionResponse.builder()
                .id(transaction.getId())
                .referenceNumber(transaction.getReferenceNumber())
                .transactionType(transaction.getTransactionType().name())
                .status(transaction.getStatus().name())
                .amount(transaction.getAmount())
                .fee(transaction.getFee())
                .currency(transaction.getCurrency())
                .direction(direction)
                .sourceAccountId(transaction.getSourceAccountId())
                .destinationAccountId(transaction.getDestinationAccountId())
                .description(transaction.getDescription())
                .valueDate(transaction.getValueDate())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
