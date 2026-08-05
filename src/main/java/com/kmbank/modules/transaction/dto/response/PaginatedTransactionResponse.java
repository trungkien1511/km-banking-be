package com.kmbank.modules.transaction.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({ "content", "page", "size", "totalElements", "totalPages", "last" })
public class PaginatedTransactionResponse {

    private List<TransactionResponse> content;

    private int page;

    private int size;

    private long totalElements;

    private int totalPages;

    private boolean last;

    /**
     * Wraps a {@link Page} of {@link TransactionResponse} objects into a
     * {@link PaginatedTransactionResponse}.
     *
     * @param pageResult the Spring Data page result
     * @return the paginated response DTO
     */
    public static PaginatedTransactionResponse from(Page<TransactionResponse> pageResult) {
        return PaginatedTransactionResponse.builder()
                .content(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }
}
