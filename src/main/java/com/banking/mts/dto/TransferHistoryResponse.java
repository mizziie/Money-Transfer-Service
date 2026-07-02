package com.banking.mts.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferHistoryResponse {
    private List<TransferItem> transfers;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private boolean hasNext;
    private boolean hasPrevious;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransferItem {
        private Long id;
        private Long fromAccountId;
        private Long toAccountId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private LocalDateTime createdAt;
    }
}
