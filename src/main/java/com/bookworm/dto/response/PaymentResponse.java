package com.bookworm.dto.response;

import com.bookworm.enums.PaymentMethod;
import com.bookworm.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private PaymentMethod method;
    private PaymentStatus status;
    private String transactionRef;
    private BigDecimal amount;
    private LocalDateTime paidAt;
}
