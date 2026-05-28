package com.kmbank.modules.account.mapper;

import com.kmbank.modules.account.dto.response.AccountResponse;
import com.kmbank.modules.account.entity.BankAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AccountMapper {

    public AccountResponse toResponse(BankAccount account) {
        if (account == null) {
            log.warn("Attempted to map null BankAccount to AccountResponse");
            return null;
        }

        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType().name())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus().name())
                .build();
    }
}
