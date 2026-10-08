package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitiateResponse {
    private String transactionRef;
    private PaymentResponse payment;
}
