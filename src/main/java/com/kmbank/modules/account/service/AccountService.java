package com.kmbank.modules.account.service;

import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.account.mapper.AccountMapper;
import com.kmbank.modules.account.repository.BankAccountRepository;
import com.kmbank.modules.customer.repository.CustomerRepository;
import com.kmbank.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final CustomerRepository customerRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountMapper accountMapper;

    public List<AccountResponse> getAccountsForCurrentUser(CustomUserPrincipal principal) {
        UUID userId = principal.getId();

        return customerRepository.findByUserId(userId)
                .map(customer -> {
                    List<AccountResponse> accounts = bankAccountRepository
                            .findByCustomerId(customer.getId())
                            .stream()
                            .map(accountMapper::toResponse)
                            .toList();
                    log.debug("Found {} account(s) for userId={}", accounts.size(), userId);
                    return accounts;
                })
                .orElseGet(() -> {
                    log.warn("No customer record found for userId={}", userId);
                    return Collections.emptyList();
                });
    }
}
