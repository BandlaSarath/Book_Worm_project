package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RedeemGiftPointsResponse {
    private BigDecimal updatedTotal;
    private BigDecimal pointsRedeemed;
    private BigDecimal remainingBalance;
}
