package com.bookworm.controller;

import com.bookworm.config.SecurityConfig;
import com.bookworm.dto.request.AddToCartRequest;
import com.bookworm.dto.request.UpdateCartItemRequest;
import com.bookworm.dto.response.CartItemResponse;
import com.bookworm.dto.response.CartResponse;
import com.bookworm.entity.User;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.security.JwtAuthenticationFilter;
import com.bookworm.security.JwtTokenProvider;
import com.bookworm.security.UserDetailsServiceImpl;
import com.bookworm.service.CartService;
import com.bookworm.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("CartController slice tests")
class CartControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CartService cartService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    private static final String USER_EMAIL = "alice@example.com";
    private static final Long   USER_ID    = 1L;

    private User mockUser;
    private CartResponse emptyCartResponse;
    private CartResponse cartWithItemResponse;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(USER_ID).email(USER_EMAIL)
                .role(UserRole.CUSTOMER).build();

        emptyCartResponse = CartResponse.builder()
                .id(100L).items(List.of())
                .subtotal(BigDecimal.ZERO).itemCount(0).build();

        CartItemResponse itemResp = CartItemResponse.builder()
                .id(1L).quantity(2)
                .priceSnapshot(new BigDecimal("29.99"))
                .lineTotal(new BigDecimal("59.98")).build();

        cartWithItemResponse = CartResponse.builder()
                .id(100L).items(List.of(itemResp))
                .subtotal(new BigDecimal("59.98")).itemCount(1).build();
    }

    // ── GET /cart ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /cart – authenticated user gets their cart (200)")
    void getCart_authenticated_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(cartService.getCart(USER_ID)).thenReturn(emptyCartResponse);

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.subtotal").value(0));
    }

    @Test
    @DisplayName("GET /cart – unauthenticated request is rejected (4xx)")
    void getCart_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().is4xxClientError());
    }

    // ── POST /cart/items ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /cart/items – valid add request returns 200 with updated cart")
    void addItem_validRequest_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(cartService.addToCart(eq(USER_ID), any())).thenReturn(cartWithItemResponse);

        var body = Map.of("bookId", 10, "quantity", 2);

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.subtotal").value(59.98));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /cart/items – quantity < 1 returns 400")
    void addItem_invalidQuantity_returns400() throws Exception {
        var body = Map.of("bookId", 10, "quantity", 0);

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /cart/items – missing bookId returns 400")
    void addItem_missingBookId_returns400() throws Exception {
        var body = Map.of("quantity", 1);

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /cart/items – nonexistent book returns 404")
    void addItem_bookNotFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(cartService.addToCart(eq(USER_ID), any()))
                .thenThrow(new ResourceNotFoundException("Book not found: 99"));

        var body = Map.of("bookId", 99, "quantity", 1);

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /cart/items – out-of-stock book returns 422")
    void addItem_insufficientStock_returns422() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(cartService.addToCart(eq(USER_ID), any()))
                .thenThrow(new BusinessException("Insufficient stock for book: Clean Code"));

        var body = Map.of("bookId", 10, "quantity", 100);

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /cart/items – unauthenticated request is rejected (4xx)")
    void addItem_unauthenticated_isRejected() throws Exception {
        var body = Map.of("bookId", 10, "quantity", 1);

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());
    }

    // ── PUT /cart/items/{itemId} ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("PUT /cart/items/{itemId} – valid update returns 200")
    void updateItem_validRequest_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(cartService.updateCartItem(eq(USER_ID), eq(1L), any()))
                .thenReturn(cartWithItemResponse);

        var body = Map.of("quantity", 3);

        mockMvc.perform(put("/api/v1/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("PUT /cart/items/{itemId} – quantity < 1 returns 400")
    void updateItem_invalidQuantity_returns400() throws Exception {
        var body = Map.of("quantity", 0);

        mockMvc.perform(put("/api/v1/cart/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("PUT /cart/items/{itemId} – item not in user cart returns 404")
    void updateItem_itemNotFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(cartService.updateCartItem(eq(USER_ID), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Cart item not found: 99"));

        var body = Map.of("quantity", 1);

        mockMvc.perform(put("/api/v1/cart/items/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /cart/items/{itemId} ────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("DELETE /cart/items/{itemId} – removes item and returns 200")
    void removeItem_validItem_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(cartService.removeCartItem(USER_ID, 1L)).thenReturn(emptyCartResponse);

        mockMvc.perform(delete("/api/v1/cart/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("DELETE /cart/items/{itemId} – item not in user cart returns 404")
    void removeItem_itemNotFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(cartService.removeCartItem(USER_ID, 99L))
                .thenThrow(new ResourceNotFoundException("Cart item not found: 99"));

        mockMvc.perform(delete("/api/v1/cart/items/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /cart/items/{itemId} – unauthenticated is rejected (4xx)")
    void removeItem_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/items/1"))
                .andExpect(status().is4xxClientError());
    }

    // ── DELETE /cart ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("DELETE /cart – clears cart and returns 204")
    void clearCart_authenticated_returns204() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);

        mockMvc.perform(delete("/api/v1/cart"))
                .andExpect(status().isNoContent());
    }
}
