package com.kmbank.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginationMeta {
    private int page;
    private int limit;
    private long total;
    private int totalPages;
    private boolean hasNextPage;
    private boolean hasPrevPage;
}
