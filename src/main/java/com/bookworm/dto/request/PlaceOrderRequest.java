package com.bookworm.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlaceOrderRequest {

    @NotNull
    private Long addressId;

    @DecimalMin("0.00")
    private BigDecimal giftPointsToRedeem = BigDecimal.ZERO;
}
