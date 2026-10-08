package com.bookworm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentCompleteRequest {

    @NotNull
    private Long orderId;

    @NotBlank
    private String transactionRef;

    /** Mock harness field – drives the simulated outcome */
    @NotBlank
    private String mockResult;   // "SUCCESS" or "FAILED"
}
