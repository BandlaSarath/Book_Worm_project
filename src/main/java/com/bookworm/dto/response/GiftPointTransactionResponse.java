package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class GiftPointTransactionResponse {
    private Long id;
    private BigDecimal delta;
    private String reason;
    private LocalDateTime createdAt;
}
