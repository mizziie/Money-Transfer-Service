package com.banking.mts.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeAccountStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;
}
