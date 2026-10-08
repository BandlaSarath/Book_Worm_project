package com.bookworm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddToWishlistRequest {

    @NotNull
    private Long bookId;
}
