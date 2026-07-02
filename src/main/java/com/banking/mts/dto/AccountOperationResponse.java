package com.banking.mts.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountOperationResponse {
    private Long accountId;
    private BigDecimal balance;
    private Long ledgerEntryId;
}
