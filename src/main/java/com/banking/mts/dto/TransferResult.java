package com.banking.mts.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResult {
    private TransferResponse response;
    private boolean replay;
}
