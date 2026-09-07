package com.kmbank.modules.transaction.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotNull(message = "Source account ID is required")
    private UUID sourceAccountId;

    @NotBlank(message = "Destination account number is required")
    private String destinationAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @DecimalMax(value = "500000000.00", message = "Amount cannot exceed 500,000,000 VND per transaction")
    private BigDecimal amount;

    private String description;

    /**
     * Mandatory client-supplied idempotency key (UUID v4 format).
     * Used to detect duplicate requests and ensure safe retry handling.
     */
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
