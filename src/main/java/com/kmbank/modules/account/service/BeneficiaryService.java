package com.kmbank.modules.account.service;

import com.kmbank.modules.account.dto.request.SaveBeneficiaryRequest;
import com.kmbank.modules.account.dto.response.BeneficiaryDto;
import com.kmbank.modules.account.entity.Beneficiary;
import com.kmbank.modules.account.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountService accountService;

    /**
     * Saves a new beneficiary. If the user already saved this account number,
     * updates the display name instead of inserting a duplicate (upsert semantics).
     *
     * @throws BusinessException ACCOUNT_NOT_FOUND if the target account does not exist
     */
    @Transactional
    public BeneficiaryDto saveBeneficiary(UUID userId, SaveBeneficiaryRequest request) {
        // Validate the account exists (throws ACCOUNT_NOT_FOUND if not)
        var lookup = accountService.lookupRecipient(request.accountNumber());

        Beneficiary beneficiary = beneficiaryRepository
                .findByUserIdAndAccountNumber(userId, request.accountNumber())
                .map(existing -> {
                    existing.setDisplayName(request.displayName());
                    return existing;
                })
                .orElseGet(() -> Beneficiary.builder()
                        .userId(userId)
                        .accountNumber(request.accountNumber())
                        .displayName(request.displayName())
                        .build());

        Beneficiary saved = beneficiaryRepository.save(beneficiary);
        return toDto(saved, lookup.accountHolderName());
    }

    /**
     * Returns all saved beneficiaries for the given user, most-recently-saved first.
     */
    @Transactional(readOnly = true)
    public List<BeneficiaryDto> getBeneficiaries(UUID userId) {
        return beneficiaryRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(b -> {
                    String maskedName = resolveMaskedName(b.getAccountNumber());
                    return toDto(b, maskedName);
                })
                .toList();
    }

    private BeneficiaryDto toDto(Beneficiary b, String maskedName) {
        return new BeneficiaryDto(b.getId(), b.getAccountNumber(),
                maskedName, b.getDisplayName(), b.getCreatedAt());
    }

    private String resolveMaskedName(String accountNumber) {
        try {
            return accountService.lookupRecipient(accountNumber).accountHolderName();
        } catch (Exception e) {
            return accountNumber;
        }
    }
}