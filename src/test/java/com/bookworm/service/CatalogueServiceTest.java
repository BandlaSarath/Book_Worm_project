package com.bookworm.service;

import com.bookworm.dto.response.AuthorResponse;
import com.bookworm.dto.response.BookResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.entity.Author;
import com.bookworm.entity.Book;
import com.bookworm.entity.Category;
import com.bookworm.enums.BookFormat;
import com.bookworm.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogueService unit tests")
class CatalogueServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private AuthorRepository authorRepository;
    @Mock private CategoryRepository categoryRepository;

    // Real mapper — no mocking needed; toPagedResponse delegates to Spring Page
    private final BookWormMapper mapper = new BookWormMapper();

    private CatalogueService catalogueService;

    private Book book;
    private Author author;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        catalogueService = new CatalogueService(bookRepository, authorRepository, categoryRepository, mapper);

        pageable = PageRequest.of(0, 20);

        // Minimal Book — collections must be initialised to avoid NPE in mapper
        book = new Book();
        book.setId(1L);
        book.setTitle("Test Book");
        book.setAuthors(new ArrayList<>());
        book.setCategories(new ArrayList<>());

        author = new Author();
        author.setId(10L);
        author.setName("Author A");
    }

    // ── searchBooks: category branch ─────────────────────────────────────────

    @Test
    @DisplayName("searchBooks – routes to findByCategoryId when categoryId is supplied")
    void searchBooks_withCategoryId_usesCategoryQuery() {
        Page<Book> bookPage = new PageImpl<>(List.of(book));
        when(bookRepository.findByCategoryId(eq(5L), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(bookPage);

        PagedResponse<BookResponse> result = catalogueService.searchBooks(
                null, 5L, null, null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        verify(bookRepository).findByCategoryId(eq(5L), isNull(), isNull(), isNull(), isNull(), eq(pageable));
        verify(bookRepository, never()).findByAuthorIdFiltered(any(), any(), any(), any(), any(), any());
        verify(bookRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    // ── searchBooks: author branch ────────────────────────────────────────────

    @Test
    @DisplayName("searchBooks – routes to findByAuthorIdFiltered when authorId is supplied")
    void searchBooks_withAuthorId_usesAuthorFilteredQuery() {
        Page<Book> bookPage = new PageImpl<>(List.of(book));
        when(bookRepository.findByAuthorIdFiltered(eq(10L), eq("English"), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(bookPage);

        PagedResponse<BookResponse> result = catalogueService.searchBooks(
                null, null, 10L, "English", null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(bookRepository).findByAuthorIdFiltered(eq(10L), eq("English"), isNull(), isNull(), isNull(), eq(pageable));
        verify(bookRepository, never()).findByCategoryId(any(), any(), any(), any(), any(), any());
        verify(bookRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    // ── searchBooks: full-text branch ─────────────────────────────────────────

    @Test
    @DisplayName("searchBooks – routes to Specification-based findAll when no categoryId or authorId")
    void searchBooks_withQuery_usesSpecificationQuery() {
        Page<Book> bookPage = new PageImpl<>(List.of(book));
        when(bookRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(bookPage);

        PagedResponse<BookResponse> result = catalogueService.searchBooks(
                "spring", null, null, null, BookFormat.EBOOK, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(bookRepository).findAll(any(Specification.class), eq(pageable));
        verify(bookRepository, never()).findByCategoryId(any(), any(), any(), any(), any(), any());
        verify(bookRepository, never()).findByAuthorIdFiltered(any(), any(), any(), any(), any(), any());
    }

    // ── getBookById ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBookById – returns BookResponse when book exists")
    void getBookById_found_returnsResponse() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        BookResponse result = catalogueService.getBookById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Test Book");
    }

    @Test
    @DisplayName("getBookById – throws ResourceNotFoundException when book not found")
    void getBookById_notFound_throwsException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogueService.getBookById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── getRelatedBooks ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRelatedBooks – returns list when book exists")
    void getRelatedBooks_bookExists_returnsList() {
        when(bookRepository.existsById(1L)).thenReturn(true);
        when(bookRepository.findRelated(eq(1L), any(Pageable.class))).thenReturn(List.of(book));

        List<BookResponse> result = catalogueService.getRelatedBooks(1L, 6);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        verify(bookRepository).findRelated(eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("getRelatedBooks – throws ResourceNotFoundException when book not found")
    void getRelatedBooks_bookNotFound_throwsException() {
        when(bookRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> catalogueService.getRelatedBooks(99L, 6))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── getBooksByCategory ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getBooksByCategory – throws ResourceNotFoundException when category not found")
    void getBooksByCategory_categoryNotFound_throwsException() {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> catalogueService.getBooksByCategory(
                99L, null, null, null, null, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("getBooksByCategory – returns paged books when category exists")
    void getBooksByCategory_found_returnsPaged() {
        Page<Book> bookPage = new PageImpl<>(List.of(book));
        when(categoryRepository.existsById(5L)).thenReturn(true);
        when(bookRepository.findByCategoryId(eq(5L), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(bookPage);

        PagedResponse<BookResponse> result = catalogueService.getBooksByCategory(
                5L, null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── getBooksByAuthor ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getBooksByAuthor – throws ResourceNotFoundException when author not found")
    void getBooksByAuthor_authorNotFound_throwsException() {
        when(authorRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> catalogueService.getBooksByAuthor(99L, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("getBooksByAuthor – returns paged books when author exists")
    void getBooksByAuthor_found_returnsPaged() {
        Page<Book> bookPage = new PageImpl<>(List.of(book));
        when(authorRepository.existsById(10L)).thenReturn(true);
        when(bookRepository.findByAuthorId(eq(10L), eq(pageable))).thenReturn(bookPage);

        PagedResponse<BookResponse> result = catalogueService.getBooksByAuthor(10L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── listAuthors ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listAuthors – calls findAll when no query supplied")
    void listAuthors_noQuery_callsFindAll() {
        Page<Author> authorPage = new PageImpl<>(List.of(author));
        when(authorRepository.findAll(pageable)).thenReturn(authorPage);

        PagedResponse<AuthorResponse> result = catalogueService.listAuthors(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Author A");
        verify(authorRepository).findAll(pageable);
        verify(authorRepository, never()).findByNameContainingIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("listAuthors – calls findByNameContainingIgnoreCase when query supplied")
    void listAuthors_withQuery_callsNameSearch() {
        Page<Author> authorPage = new PageImpl<>(List.of(author));
        when(authorRepository.findByNameContainingIgnoreCase(eq("Author"), eq(pageable)))
                .thenReturn(authorPage);

        PagedResponse<AuthorResponse> result = catalogueService.listAuthors("Author", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Author A");
        verify(authorRepository).findByNameContainingIgnoreCase("Author", pageable);
        verify(authorRepository, never()).findAll(any(Pageable.class));
    }

    // ── regression: null/blank q — Specification path (lower(bytea) fix) ────────

    @Test
    @DisplayName("searchBooks – null q uses Specification path (no lower(bytea) error)")
    void searchBooks_nullQ_usesSpecification() {
        when(bookRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        PagedResponse<BookResponse> result =
                catalogueService.searchBooks(null, null, null, null, null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(bookRepository).findAll(any(Specification.class), eq(pageable));
        verify(bookRepository, never()).findByCategoryId(any(), any(), any(), any(), any(), any());
        verify(bookRepository, never()).findByAuthorIdFiltered(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("searchBooks – blank q uses Specification path (blank treated as no-filter)")
    void searchBooks_blankQ_usesSpecification() {
        when(bookRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        PagedResponse<BookResponse> result =
                catalogueService.searchBooks("   ", null, null, null, null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        // blank string → Specification built without a text-search predicate (isBlank check inside buildSearchSpec)
        verify(bookRepository).findAll(any(Specification.class), eq(pageable));
        verify(bookRepository, never()).findByCategoryId(any(), any(), any(), any(), any(), any());
        verify(bookRepository, never()).findByAuthorIdFiltered(any(), any(), any(), any(), any(), any());
    }
}
