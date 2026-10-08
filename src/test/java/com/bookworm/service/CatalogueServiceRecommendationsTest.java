package com.bookworm.service;

import com.bookworm.dto.response.BookResponse;
import com.bookworm.entity.Book;
import com.bookworm.enums.BookAvailability;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.AuthorRepository;
import com.bookworm.repository.BookRepository;
import com.bookworm.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogueService – getRecommendations unit tests")
class CatalogueServiceRecommendationsTest {

    @Mock private BookRepository bookRepository;
    @Mock private AuthorRepository authorRepository;
    @Mock private CategoryRepository categoryRepository;

    private final BookWormMapper mapper = new BookWormMapper();
    private CatalogueService catalogueService;

    private static final Long USER_ID       = 1L;
    private static final Long OTHER_USER_ID = 99L;

    private Book book;

    @BeforeEach
    void setUp() {
        catalogueService = new CatalogueService(bookRepository, authorRepository,
                categoryRepository, mapper);

        book = Book.builder()
                .id(42L).title("Refactoring")
                .price(new BigDecimal("34.99")).stock(3)
                .availability(BookAvailability.IN_STOCK)
                .authors(new ArrayList<>()).categories(new ArrayList<>())
                .build();
    }

    // ── item 1 + 3: user with history receives recommendations ─────────────────

    @Test
    @DisplayName("getRecommendations – returns books for user with purchase history")
    void getRecommendations_userWithHistory_returnsBooks() {
        when(bookRepository.findRecommendations(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(book));

        List<BookResponse> result = catalogueService.getRecommendations(USER_ID, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(42L);
        assertThat(result.get(0).getTitle()).isEqualTo("Refactoring");
    }

    // ── item 4: user with no purchase history handled gracefully ───────────────

    @Test
    @DisplayName("getRecommendations – returns empty list when user has no purchase history")
    void getRecommendations_noPurchaseHistory_returnsEmptyList() {
        when(bookRepository.findRecommendations(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        List<BookResponse> result = catalogueService.getRecommendations(USER_ID, 10);

        assertThat(result).isEmpty();
    }

    // ── item 2: userId scoped — another user's ID produces different result ────

    @Test
    @DisplayName("getRecommendations – uses caller's userId, not another user's")
    void getRecommendations_usesCorrectUserId() {
        when(bookRepository.findRecommendations(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(book));
        when(bookRepository.findRecommendations(eq(OTHER_USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        List<BookResponse> resultUser    = catalogueService.getRecommendations(USER_ID, 10);
        List<BookResponse> resultOther   = catalogueService.getRecommendations(OTHER_USER_ID, 10);

        assertThat(resultUser).hasSize(1);
        assertThat(resultOther).isEmpty();
        verify(bookRepository).findRecommendations(eq(USER_ID), any(Pageable.class));
        verify(bookRepository).findRecommendations(eq(OTHER_USER_ID), any(Pageable.class));
    }

    // ── limit is forwarded to the repository ──────────────────────────────────

    @Test
    @DisplayName("getRecommendations – limit is forwarded to repository as Pageable size")
    void getRecommendations_limitForwardedToRepository() {
        when(bookRepository.findRecommendations(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        catalogueService.getRecommendations(USER_ID, 5);

        verify(bookRepository).findRecommendations(eq(USER_ID),
                argThat(p -> p.getPageSize() == 5));
    }

    // ── item 5: mapped responses contain valid book data ──────────────────────

    @Test
    @DisplayName("getRecommendations – response contains valid book fields")
    void getRecommendations_responseContainsValidFields() {
        when(bookRepository.findRecommendations(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(book));

        List<BookResponse> result = catalogueService.getRecommendations(USER_ID, 10);

        BookResponse resp = result.get(0);
        assertThat(resp.getId()).isEqualTo(42L);
        assertThat(resp.getTitle()).isNotBlank();
        assertThat(resp.getPrice()).isEqualByComparingTo("34.99");
    }
}
