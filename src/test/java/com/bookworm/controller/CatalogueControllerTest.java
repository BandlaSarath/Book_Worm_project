package com.bookworm.controller;

import com.bookworm.config.SecurityConfig;
import com.bookworm.dto.response.AuthorResponse;
import com.bookworm.dto.response.BookResponse;
import com.bookworm.dto.response.CategoryResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.enums.BookFormat;
import com.bookworm.security.JwtAuthenticationFilter;
import com.bookworm.security.JwtTokenProvider;
import com.bookworm.security.UserDetailsServiceImpl;
import com.bookworm.service.CatalogueService;
import com.bookworm.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogueController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("CatalogueController slice tests")
class CatalogueControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CatalogueService catalogueService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    // ── /books ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /books – default params returns 200 with paged content")
    void listBooks_defaultParams_returns200() throws Exception {
        PagedResponse<BookResponse> paged = pagedOf(
                BookResponse.builder().id(1L).title("Clean Code").build());
        when(catalogueService.searchBooks(isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Clean Code"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /books – with q and format filter routes correctly")
    void listBooks_withFilters_returns200() throws Exception {
        PagedResponse<BookResponse> paged = pagedOf(
                BookResponse.builder().id(2L).title("Spring in Action").build());
        when(catalogueService.searchBooks(eq("Spring"), isNull(), isNull(), isNull(),
                eq(BookFormat.PAPERBACK), isNull(), isNull(), any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/books")
                        .param("q", "Spring")
                        .param("format", "PAPERBACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2));
    }

    @Test
    @DisplayName("GET /books – page < 0 returns 400")
    void listBooks_negativePageParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /books – size > 100 returns 400")
    void listBooks_oversizedSizeParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("size", "200"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /books – invalid format enum returns 400")
    void listBooks_invalidFormatEnum_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("format", "INVALID_FORMAT"))
                .andExpect(status().isBadRequest());
    }

    // ── /books/{bookId} ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /books/{bookId} – found returns 200 with book")
    void getBook_found_returns200() throws Exception {
        BookResponse resp = BookResponse.builder().id(5L).title("Design Patterns").build();
        when(catalogueService.getBookById(5L)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/books/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Design Patterns"));
    }

    @Test
    @DisplayName("GET /books/{bookId} – bookId <= 0 returns 400")
    void getBook_invalidId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/books/0"))
                .andExpect(status().isBadRequest());
    }

    // ── /books/{bookId}/related ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /books/{bookId}/related – returns 200 with list")
    void getRelatedBooks_returns200() throws Exception {
        List<BookResponse> related = List.of(
                BookResponse.builder().id(2L).title("Refactoring").build());
        when(catalogueService.getRelatedBooks(eq(1L), eq(6))).thenReturn(related);

        mockMvc.perform(get("/api/v1/books/1/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Refactoring"));
    }

    @Test
    @DisplayName("GET /books/{bookId}/related – limit > 20 returns 400")
    void getRelatedBooks_limitTooLarge_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/books/1/related").param("limit", "50"))
                .andExpect(status().isBadRequest());
    }

    // ── /categories ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /categories – returns 200 with category list")
    void listCategories_returns200() throws Exception {
        List<CategoryResponse> cats = List.of(
                CategoryResponse.builder().id(1L).name("Fiction").build());
        when(catalogueService.listCategories()).thenReturn(cats);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Fiction"));
    }

    @Test
    @DisplayName("GET /categories/{categoryId} – found returns 200")
    void getCategoryById_found_returns200() throws Exception {
        CategoryResponse cat = CategoryResponse.builder().id(3L).name("Science").build();
        when(catalogueService.getCategoryById(3L)).thenReturn(cat);

        mockMvc.perform(get("/api/v1/categories/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Science"));
    }

    @Test
    @DisplayName("GET /categories/{categoryId} – id <= 0 returns 400")
    void getCategoryById_invalidId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/categories/0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /categories/{categoryId}/books – returns 200 with paged books")
    void getBooksByCategory_returns200() throws Exception {
        PagedResponse<BookResponse> paged = pagedOf(
                BookResponse.builder().id(7L).title("Dune").build());
        when(catalogueService.getBooksByCategory(eq(2L), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/categories/2/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7));
    }

    // ── /authors ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /authors – default params returns 200")
    void listAuthors_returns200() throws Exception {
        PagedResponse<AuthorResponse> paged = pagedOf(
                AuthorResponse.builder().id(1L).name("Robert Martin").build());
        when(catalogueService.listAuthors(isNull(), any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/authors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Robert Martin"));
    }

    @Test
    @DisplayName("GET /authors – with q param passes it to service")
    void listAuthors_withQuery_returns200() throws Exception {
        PagedResponse<AuthorResponse> paged = pagedOf(
                AuthorResponse.builder().id(2L).name("Martin Fowler").build());
        when(catalogueService.listAuthors(eq("Martin"), any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/authors").param("q", "Martin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2));
    }

    @Test
    @DisplayName("GET /authors/{authorId} – found returns 200")
    void getAuthor_found_returns200() throws Exception {
        AuthorResponse resp = AuthorResponse.builder().id(4L).name("Kent Beck").build();
        when(catalogueService.getAuthorById(4L)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/authors/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kent Beck"));
    }

    @Test
    @DisplayName("GET /authors/{authorId} – id <= 0 returns 400")
    void getAuthor_invalidId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/authors/0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /authors/{authorId}/books – returns 200 with paged books")
    void getBooksByAuthor_returns200() throws Exception {
        PagedResponse<BookResponse> paged = pagedOf(
                BookResponse.builder().id(9L).title("Extreme Programming").build());
        when(catalogueService.getBooksByAuthor(eq(4L), any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/authors/4/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Extreme Programming"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private <T> PagedResponse<T> pagedOf(T... items) {
        return PagedResponse.<T>builder()
                .content(List.of(items))
                .page(0)
                .size(20)
                .totalElements(items.length)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }
}
