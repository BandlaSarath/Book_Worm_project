package com.bookworm.controller;

import com.bookworm.config.SecurityConfig;
import com.bookworm.dto.response.*;
import com.bookworm.entity.User;
import com.bookworm.enums.OrderStatus;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.security.JwtAuthenticationFilter;
import com.bookworm.security.JwtTokenProvider;
import com.bookworm.security.UserDetailsServiceImpl;
import com.bookworm.service.OrderService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("OrderController slice tests")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private OrderService orderService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    private static final String USER_EMAIL = "alice@example.com";
    private static final Long   USER_ID    = 1L;

    private User mockUser;
    private OrderResponse orderResp;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(USER_ID).email(USER_EMAIL).role(UserRole.CUSTOMER).build();

        orderResp = OrderResponse.builder()
                .id(10L)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("29.99"))
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("29.99"))
                .items(List.of())
                .placedAt(LocalDateTime.now())
                .cancellationEligible(true)
                .build();
    }

    // ── POST /orders ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /orders – valid request creates order and returns 201")
    void placeOrder_validRequest_returns201() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.placeOrder(eq(mockUser), any())).thenReturn(orderResp);

        var body = Map.of("addressId", 5, "giftPointsToRedeem", 0);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /orders – missing addressId returns 400")
    void placeOrder_missingAddressId_returns400() throws Exception {
        var body = Map.of("giftPointsToRedeem", 0);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /orders – empty cart returns 422")
    void placeOrder_emptyCart_returns422() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.placeOrder(eq(mockUser), any()))
                .thenThrow(new BusinessException("Cannot place order with empty cart"));

        var body = Map.of("addressId", 5, "giftPointsToRedeem", 0);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /orders – address not found returns 404")
    void placeOrder_addressNotFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.placeOrder(eq(mockUser), any()))
                .thenThrow(new ResourceNotFoundException("Address not found: 99"));

        var body = Map.of("addressId", 99, "giftPointsToRedeem", 0);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /orders – unauthenticated is rejected (4xx)")
    void placeOrder_unauthenticated_isRejected() throws Exception {
        var body = Map.of("addressId", 5, "giftPointsToRedeem", 0);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());
    }

    // ── GET /orders ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /orders – returns paged order history for authenticated user (200)")
    void listOrders_authenticated_returns200() throws Exception {
        PagedResponse<OrderResponse> paged = PagedResponse.<OrderResponse>builder()
                .content(List.of(orderResp)).page(0).size(20)
                .totalElements(1).totalPages(1).first(true).last(true).build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.listOrders(eq(USER_ID), isNull(), any())).thenReturn(paged);

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /orders – status filter is passed to service")
    void listOrders_withStatusFilter_returns200() throws Exception {
        PagedResponse<OrderResponse> paged = PagedResponse.<OrderResponse>builder()
                .content(List.of()).page(0).size(20)
                .totalElements(0).totalPages(0).first(true).last(true).build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.listOrders(eq(USER_ID), eq(OrderStatus.CONFIRMED), any()))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/orders").param("status", "CONFIRMED"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /orders – unauthenticated is rejected (4xx)")
    void listOrders_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().is4xxClientError());
    }

    // ── GET /orders/{orderId} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /orders/{id} – returns order belonging to user (200)")
    void getOrder_ownedOrder_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.getOrder(USER_ID, 10L)).thenReturn(orderResp);

        mockMvc.perform(get("/api/v1/orders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /orders/{id} – order not found or belongs to another user returns 404")
    void getOrder_notFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.getOrder(USER_ID, 99L))
                .thenThrow(new ResourceNotFoundException("Order not found: 99"));

        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound());
    }

    // ── POST /orders/{orderId}/cancel ──────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /orders/{id}/cancel – within window returns 200 with CANCELLED status")
    void cancelOrder_withinWindow_returns200() throws Exception {
        OrderResponse cancelled = OrderResponse.builder()
                .id(10L).status(OrderStatus.CANCELLED)
                .subtotal(new BigDecimal("29.99"))
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("29.99"))
                .items(List.of())
                .placedAt(LocalDateTime.now().minusHours(1))
                .cancellationEligible(false)
                .build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.cancelOrder(USER_ID, 10L)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/orders/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /orders/{id}/cancel – beyond 48-hour window returns 422")
    void cancelOrder_beyondWindow_returns422() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.cancelOrder(USER_ID, 10L))
                .thenThrow(new BusinessException("Order cannot be cancelled: past the 48-hour window"));

        mockMvc.perform(post("/api/v1/orders/10/cancel"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── POST /orders/{orderId}/buy-again ───────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /orders/{id}/buy-again – returns 200 with cart and skipped items")
    void buyAgain_returns200() throws Exception {
        CartResponse cartResp = CartResponse.builder()
                .id(100L).items(List.of())
                .subtotal(BigDecimal.ZERO).itemCount(0).build();
        BuyAgainResponse buyAgainResp = BuyAgainResponse.builder()
                .cart(cartResp).skippedItems(List.of()).build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.buyAgain(eq(mockUser), eq(10L))).thenReturn(buyAgainResp);

        mockMvc.perform(post("/api/v1/orders/10/buy-again"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart.id").value(100))
                .andExpect(jsonPath("$.skippedItems").isArray());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /orders/{id}/buy-again – order not found returns 404")
    void buyAgain_orderNotFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(orderService.buyAgain(eq(mockUser), eq(99L)))
                .thenThrow(new ResourceNotFoundException("Order not found: 99"));

        mockMvc.perform(post("/api/v1/orders/99/buy-again"))
                .andExpect(status().isNotFound());
    }
}
