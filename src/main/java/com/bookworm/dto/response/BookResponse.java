package com.bookworm.dto.response;

import com.bookworm.enums.BookAvailability;
import com.bookworm.enums.BookFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class BookResponse {
    private Long id;
    private String title;
    private List<AuthorResponse> authors;
    private List<CategoryResponse> categories;
    private BigDecimal price;
    private BookFormat format;
    private String language;
    private String coverImageUrl;
    private String isbn;
    private String description;
    private String publisher;
    private LocalDate publishedDate;
    private Integer stock;
    private BookAvailability availability;
    private String deliveryInfo;
}
