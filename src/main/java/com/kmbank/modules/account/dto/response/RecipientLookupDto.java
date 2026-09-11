package com.kmbank.modules.account.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Lightweight DTO for recipient account verification.
 *
 * <p>Intentionally exposes minimal data — no balance, no ID, no customer UUID.
 * The masked name follows Vietnamese banking privacy convention: only initials
 * of middle parts are shown (e.g. "NGUYEN VAN ANH" → "NGUYEN V** A").
 */
@JsonPropertyOrder({"accountNumber", "accountHolderName", "status"})
public record RecipientLookupDto(

        String accountNumber,

        /**
         * Partially masked full name of the account holder.
         * Masking is applied server-side before serialisation.
         */
        String accountHolderName,

        /**
         * Account status string: ACTIVE, FROZEN, INACTIVE, CLOSED.
         * Frontend uses this to warn users before they attempt a transfer
         * to a non-ACTIVE account.
         */
        String status
) {}
