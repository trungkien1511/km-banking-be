package com.kmbank.modules.account.service;

import com.kmbank.modules.account.dto.response.RecentRecipientDto;
import com.kmbank.modules.account.entity.RecentRecipient;
import com.kmbank.modules.account.repository.RecentRecipientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecentRecipientService {

    private final RecentRecipientRepository recentRecipientRepository;
    private final AccountService accountService;

    /**
     * Records a successful transfer to update the recent recipients list.
     * Uses upsert: increments count if row exists, otherwise inserts new row.
     * Runs in its own transaction — failure here must NOT roll back the transfer.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackTransfer(UUID userId, String recipientAccountNumber) {
        try {
            Instant now = Instant.now();
            int updated = recentRecipientRepository
                    .incrementTransferCount(userId, recipientAccountNumber, now);
            if (updated == 0) {
                RecentRecipient entry = RecentRecipient.builder()
                        .userId(userId)
                        .recipientAccountNumber(recipientAccountNumber)
                        .transferCount(1)
                        .lastTransferAt(now)
                        .build();
                recentRecipientRepository.save(entry);
            }
        } catch (Exception e) {
            // Non-critical — log and swallow; never fail a transfer for this
            log.warn("Failed to track recent recipient: userId={}, accountNumber={}, reason={}",
                    userId, recipientAccountNumber, e.getMessage());
        }
    }

    /**
     * Returns the user's most-recently-used transfer recipients with masked names.
     *
     * @param userId the authenticated user's UUID
     * @param limit  max number of results (1–20; clamped if out of range)
     */
    @Transactional(readOnly = true)
    public List<RecentRecipientDto> getRecentRecipients(UUID userId, int limit) {
        int safeLimit = Math.max(1, Math.min(20, limit));
        return recentRecipientRepository
                .findByUserIdOrderByLastTransferAtDesc(userId, PageRequest.of(0, safeLimit))
                .stream()
                .map(r -> {
                    // Resolve masked name via existing recipient lookup (returns "" if account closed)
                    String maskedName = resolveMaskedName(r.getRecipientAccountNumber());
                    return new RecentRecipientDto(
                            r.getRecipientAccountNumber(),
                            maskedName,
                            r.getLastTransferAt());
                })
                .toList();
    }

    private String resolveMaskedName(String accountNumber) {
        try {
            return accountService.lookupRecipient(accountNumber).accountHolderName();
        } catch (Exception e) {
            return accountNumber; // fallback: show account number if lookup fails
        }
    }
}