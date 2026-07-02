package com.banking.mts.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {
    private String accountNumber;
    private BigDecimal balance;
    private String currency;
}
