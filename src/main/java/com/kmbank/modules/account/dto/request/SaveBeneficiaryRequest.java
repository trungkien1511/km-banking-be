package com.kmbank.modules.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveBeneficiaryRequest(
    @NotBlank(message = "Account number is required")
    String accountNumber,

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must be 100 characters or less")
    String displayName
) {}