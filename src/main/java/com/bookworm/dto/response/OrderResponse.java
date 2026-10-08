package com.bookworm.dto.response;

import com.bookworm.enums.OrderStatus;
import com.bookworm.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private List<OrderItemResponse> items;
    private AddressResponse deliveryAddress;
    private OrderStatus status;
    private BigDecimal subtotal;
    private BigDecimal giftPointsRedeemed;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private LocalDateTime placedAt;
    private LocalDateTime cancelledAt;
    private boolean cancellationEligible;
}
