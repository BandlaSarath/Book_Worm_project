package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseConfirmationResponse {
    private OrderResponse order;
    private PaymentResponse payment;
    private String message;
}
