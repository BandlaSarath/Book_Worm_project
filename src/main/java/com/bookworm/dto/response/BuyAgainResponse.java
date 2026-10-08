package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BuyAgainResponse {
    private CartResponse cart;
    private List<BookResponse> skippedItems;
}
