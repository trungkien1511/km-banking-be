package com.kmbank.modules.transaction.mapper;

import com.kmbank.modules.transaction.dto.response.RecentTransactionResponse;
import com.kmbank.modules.transaction.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class TransactionMapper {

    public RecentTransactionResponse toRecentResponse(Transaction transaction, Set<UUID> userAccountIds) {
        String direction;
        if (transaction.getDestinationAccountId() != null
                && userAccountIds.contains(transaction.getDestinationAccountId())) {
            direction = "IN";
        } else {
            direction = "OUT";
        }

        return RecentTransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .transactionType(direction)
                .description(transaction.getDescription())
                .initiatedAt(transaction.getInitiatedAt())
                .build();
    }
}
