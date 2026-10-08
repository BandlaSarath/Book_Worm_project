package com.bookworm.entity;

import com.bookworm.enums.BookAvailability;
import com.bookworm.enums.BookFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "books")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String isbn;

    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 50)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BookFormat format;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Column(length = 200)
    private String publisher;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "delivery_info", length = 300)
    private String deliveryInfo;

    @Min(0)
    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private BookAvailability availability = BookAvailability.IN_STOCK;

    @ManyToMany
    @JoinTable(
        name = "book_authors",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    @Builder.Default
    private List<Author> authors = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "book_categories",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private List<Category> categories = new ArrayList<>();
}
