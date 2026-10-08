package com.bookworm.controller;

import com.bookworm.config.SecurityConfig;
import com.bookworm.dto.response.BookResponse;
import com.bookworm.entity.User;
import com.bookworm.enums.UserRole;
import com.bookworm.security.JwtAuthenticationFilter;
import com.bookworm.security.JwtTokenProvider;
import com.bookworm.security.UserDetailsServiceImpl;
import com.bookworm.service.CatalogueService;
import com.bookworm.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogueController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("CatalogueController – recommendations slice tests")
class RecommendationsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CatalogueService catalogueService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    private static final String USER_EMAIL = "alice@example.com";
    private static final Long   USER_ID    = 1L;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(USER_ID).email(USER_EMAIL).role(UserRole.CUSTOMER).build();
    }

    // ── item 6 + auth: unauthenticated request is rejected ────────────────────

    @Test
    @DisplayName("GET /recommendations – unauthenticated request is rejected (4xx)")
    void getRecommendations_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().is4xxClientError());
    }

    // ── item 1 + 3: authenticated user receives recommendations ───────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /recommendations – authenticated user receives book list (200)")
    void getRecommendations_authenticated_returns200() throws Exception {
        BookResponse bookResp = BookResponse.builder()
                .id(42L).title("Refactoring")
                .price(new BigDecimal("34.99")).build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(catalogueService.getRecommendations(eq(USER_ID), eq(10)))
                .thenReturn(List.of(bookResp));

        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].title").value("Refactoring"));
    }

    // ── item 4: empty list returned gracefully ────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /recommendations – user with no history receives empty list (200)")
    void getRecommendations_noHistory_returnsEmptyList() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(catalogueService.getRecommendations(eq(USER_ID), eq(10)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── limit param constraints ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /recommendations – custom limit within range returns 200")
    void getRecommendations_customLimit_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(catalogueService.getRecommendations(eq(USER_ID), eq(5)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recommendations").param("limit", "5"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /recommendations – limit > 20 returns 400")
    void getRecommendations_limitTooLarge_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations").param("limit", "50"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /recommendations – limit < 1 returns 400")
    void getRecommendations_limitTooSmall_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }
}
