package com.bookworm.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RedeemGiftPointsRequest {

    @NotNull
    private Long orderId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
}
