package com.kmbank.modules.account.dto.response;

import java.time.Instant;

public record RecentRecipientDto(
    String accountNumber,
    String accountHolderName,
    Instant lastTransferAt
) {}