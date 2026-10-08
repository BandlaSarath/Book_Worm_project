package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartItemResponse {
    private Long id;
    private BookResponse book;
    private int quantity;
    private BigDecimal priceSnapshot;
    private BigDecimal lineTotal;
}
