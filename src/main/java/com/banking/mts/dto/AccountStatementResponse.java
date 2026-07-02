package com.banking.mts.dto;

import com.banking.mts.domain.enums.EntryType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountStatementResponse {
    private Long accountId;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private List<TransactionItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionItem {
        private Long id;
        private EntryType entryType;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private Long transferId;
        private LocalDateTime createdAt;
    }
}
