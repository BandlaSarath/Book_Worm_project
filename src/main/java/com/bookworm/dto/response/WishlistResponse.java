package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class WishlistResponse {
    private List<WishlistItemResponse> items;
    private int total;
}
