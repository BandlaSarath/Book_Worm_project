package com.bookworm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WishlistItemResponse {
    private Long id;
    private BookResponse book;
    private LocalDateTime addedAt;
}
