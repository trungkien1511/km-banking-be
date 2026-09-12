package com.kmbank.modules.account.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BeneficiaryDto(
    UUID id,
    String accountNumber,
    String accountHolderName,  // masked via AccountService.lookupRecipient
    String displayName,
    Instant createdAt
) {}