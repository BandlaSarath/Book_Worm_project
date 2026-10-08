package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private BookResponse book;
    private int quantity;
    private BigDecimal priceAtPurchase;
    private BigDecimal lineTotal;
}
