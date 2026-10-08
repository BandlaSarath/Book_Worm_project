package com.bookworm.controller;

import com.bookworm.dto.response.AuthorResponse;
import com.bookworm.dto.response.BookResponse;
import com.bookworm.dto.response.CategoryResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.enums.BookFormat;
import com.bookworm.service.CatalogueService;
import com.bookworm.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CatalogueController extends BaseController {

    private final CatalogueService catalogueService;

    public CatalogueController(CatalogueService catalogueService, UserService userService) {
        super(userService);
        this.catalogueService = catalogueService;
    }

    // ── Books ──────────────────────────────────────────────────────────────
    @GetMapping("/books")
    public ResponseEntity<PagedResponse<BookResponse>> listBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) BookFormat format,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "RELEVANCE") String sortBy,
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = buildPageable(page, size, sortBy);
        return ResponseEntity.ok(catalogueService.searchBooks(
                q, categoryId, authorId, language, format, minPrice, maxPrice, pageable));
    }

    @GetMapping("/books/{bookId}")
    public ResponseEntity<BookResponse> getBook(@Positive @PathVariable Long bookId) {
        return ResponseEntity.ok(catalogueService.getBookById(bookId));
    }

    @GetMapping("/books/{bookId}/related")
    public ResponseEntity<List<BookResponse>> getRelatedBooks(
            @Positive @PathVariable Long bookId,
            @Min(1) @Max(20) @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(catalogueService.getRelatedBooks(bookId, limit));
    }

    // ── Recommendations ─────────────────────────────────────────────────
    @GetMapping("/recommendations")
    public ResponseEntity<List<BookResponse>> getRecommendations(
            Authentication auth,
            @Min(1) @Max(20) @RequestParam(defaultValue = "10") int limit) {
        Long userId = currentUser(auth).getId();
        return ResponseEntity.ok(catalogueService.getRecommendations(userId, limit));
    }

    // ── Categories ─────────────────────────────────────────────────────────
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(catalogueService.listCategories());
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryResponse> getCategoryById(
            @Positive @PathVariable Long categoryId) {
        return ResponseEntity.ok(catalogueService.getCategoryById(categoryId));
    }

    @GetMapping("/categories/{categoryId}/books")
    public ResponseEntity<PagedResponse<BookResponse>> getBooksByCategory(
            @Positive @PathVariable Long categoryId,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) BookFormat format,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "RELEVANCE") String sortBy,
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = buildPageable(page, size, sortBy);
        return ResponseEntity.ok(catalogueService.getBooksByCategory(
                categoryId, language, format, minPrice, maxPrice, pageable));
    }

    // ── Authors ────────────────────────────────────────────────────────────
    @GetMapping("/authors")
    public ResponseEntity<PagedResponse<AuthorResponse>> listAuthors(
            @RequestParam(required = false) String q,
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(catalogueService.listAuthors(q, PageRequest.of(page, size)));
    }

    @GetMapping("/authors/{authorId}")
    public ResponseEntity<AuthorResponse> getAuthor(@Positive @PathVariable Long authorId) {
        return ResponseEntity.ok(catalogueService.getAuthorById(authorId));
    }

    @GetMapping("/authors/{authorId}/books")
    public ResponseEntity<PagedResponse<BookResponse>> getBooksByAuthor(
            @Positive @PathVariable Long authorId,
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(catalogueService.getBooksByAuthor(
                authorId, PageRequest.of(page, size)));
    }

    // ── Pageable helper ────────────────────────────────────────────────────
    private Pageable buildPageable(int page, int size, String sortBy) {
        Sort sort = switch (sortBy.toUpperCase()) {
            case "PRICE_ASC"  -> Sort.by("price").ascending();
            case "PRICE_DESC" -> Sort.by("price").descending();
            case "TITLE_ASC"  -> Sort.by("title").ascending();
            case "NEWEST"     -> Sort.by("publishedDate").descending();
            default           -> Sort.unsorted();
        };
        return PageRequest.of(page, size, sort);
    }
}
