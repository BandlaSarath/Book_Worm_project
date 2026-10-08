package com.bookworm.controller;

import com.bookworm.config.SecurityConfig;
import com.bookworm.dto.response.*;
import com.bookworm.entity.User;
import com.bookworm.enums.OrderStatus;
import com.bookworm.enums.PaymentMethod;
import com.bookworm.enums.PaymentStatus;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.security.JwtAuthenticationFilter;
import com.bookworm.security.JwtTokenProvider;
import com.bookworm.security.UserDetailsServiceImpl;
import com.bookworm.service.PaymentService;
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

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("PaymentController slice tests")
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PaymentService paymentService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    private static final String USER_EMAIL = "alice@example.com";
    private static final Long   USER_ID    = 1L;

    private User mockUser;
    private PaymentResponse paymentResp;
    private OrderResponse orderResp;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(USER_ID).email(USER_EMAIL).role(UserRole.CUSTOMER).build();

        paymentResp = PaymentResponse.builder()
                .id(1L).orderId(10L)
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PENDING)
                .transactionRef("TXN-ABC")
                .amount(new BigDecimal("29.99"))
                .build();

        orderResp = OrderResponse.builder()
                .id(10L).status(OrderStatus.CONFIRMED)
                .subtotal(new BigDecimal("29.99"))
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("29.99"))
                .items(List.of())
                .placedAt(LocalDateTime.now())
                .cancellationEligible(false)
                .build();
    }

    // ── B: POST /payments/initiate ────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /payments/initiate – valid request returns 200 with transactionRef")
    void initiate_validRequest_returns200() throws Exception {
        PaymentInitiateResponse resp = PaymentInitiateResponse.builder()
                .transactionRef("TXN-ABC").payment(paymentResp).build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(paymentService.initiatePayment(eq(USER_ID), any())).thenReturn(resp);

        var body = Map.of("orderId", 10, "method", "CREDIT_CARD");

        mockMvc.perform(post("/api/v1/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionRef").value("TXN-ABC"))
                .andExpect(jsonPath("$.payment.method").value("CREDIT_CARD"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /payments/initiate – all four payment methods accepted")
    void initiate_allPaymentMethods_accepted() throws Exception {
        // Verifies enum values CREDIT_CARD, DEBIT_CARD, UPI, WALLET are all valid
        for (String method : new String[]{"CREDIT_CARD", "DEBIT_CARD", "UPI", "WALLET"}) {
            PaymentInitiateResponse resp = PaymentInitiateResponse.builder()
                    .transactionRef("TXN-" + method).payment(paymentResp).build();
            when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
            when(paymentService.initiatePayment(eq(USER_ID), any())).thenReturn(resp);

            var body = Map.of("orderId", 10, "method", method);
            mockMvc.perform(post("/api/v1/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /payments/initiate – missing method returns 400")
    void initiate_missingMethod_returns400() throws Exception {
        var body = Map.of("orderId", 10);

        mockMvc.perform(post("/api/v1/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /payments/initiate – order not found returns 404")
    void initiate_orderNotFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(paymentService.initiatePayment(eq(USER_ID), any()))
                .thenThrow(new ResourceNotFoundException("Order not found: 99"));

        var body = Map.of("orderId", 99, "method", "UPI");

        mockMvc.perform(post("/api/v1/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /payments/initiate – unauthenticated is rejected (4xx)")
    void initiate_unauthenticated_isRejected() throws Exception {
        var body = Map.of("orderId", 10, "method", "UPI");

        mockMvc.perform(post("/api/v1/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());
    }

    // ── B+C: POST /payments/complete ──────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /payments/complete – SUCCESS returns 200 with confirmation (C: no card data)")
    void complete_success_returns200WithConfirmation() throws Exception {
        PaymentResponse confirmedPayment = PaymentResponse.builder()
                .id(1L).orderId(10L)
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.SUCCESS)
                .transactionRef("TXN-ABC")
                .amount(new BigDecimal("29.99"))
                .paidAt(LocalDateTime.now())
                .build();

        PurchaseConfirmationResponse confirmation = PurchaseConfirmationResponse.builder()
                .order(orderResp).payment(confirmedPayment)
                .message("Your order has been confirmed. Thank you for shopping with Book Worm!")
                .build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(paymentService.completePayment(eq(USER_ID), any())).thenReturn(confirmation);

        var body = Map.of("orderId", 10, "transactionRef", "TXN-ABC", "mockResult", "SUCCESS");

        mockMvc.perform(post("/api/v1/payments/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                // C: order info present
                .andExpect(jsonPath("$.order.id").value(10))
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.order.totalAmount").value(29.99))
                // B+C: payment info present, no card/CVV fields in response
                .andExpect(jsonPath("$.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.payment.transactionRef").value("TXN-ABC"))
                .andExpect(jsonPath("$.payment.cardNumber").doesNotExist())
                .andExpect(jsonPath("$.payment.cvv").doesNotExist())
                // C: confirmation message present
                .andExpect(jsonPath("$.message").value(
                        "Your order has been confirmed. Thank you for shopping with Book Worm!"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /payments/complete – FAILED returns 200 with failure message")
    void complete_failed_returns200WithFailureMessage() throws Exception {
        PaymentResponse failedPayment = PaymentResponse.builder()
                .id(1L).orderId(10L).method(PaymentMethod.UPI)
                .status(PaymentStatus.FAILED).transactionRef("TXN-XYZ")
                .amount(new BigDecimal("29.99")).build();

        PurchaseConfirmationResponse confirmation = PurchaseConfirmationResponse.builder()
                .order(orderResp).payment(failedPayment)
                .message("Payment failed. Please try again.").build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(paymentService.completePayment(eq(USER_ID), any())).thenReturn(confirmation);

        var body = Map.of("orderId", 10, "transactionRef", "TXN-XYZ", "mockResult", "FAILED");

        mockMvc.perform(post("/api/v1/payments/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Payment failed. Please try again."));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /payments/complete – missing transactionRef returns 400")
    void complete_missingTransactionRef_returns400() throws Exception {
        var body = Map.of("orderId", 10, "mockResult", "SUCCESS");

        mockMvc.perform(post("/api/v1/payments/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /payments/complete – mismatched transactionRef returns 422")
    void complete_mismatchedRef_returns422() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(paymentService.completePayment(eq(USER_ID), any()))
                .thenThrow(new BusinessException("Transaction reference does not match the order"));

        var body = Map.of("orderId", 10, "transactionRef", "TXN-WRONG", "mockResult", "SUCCESS");

        mockMvc.perform(post("/api/v1/payments/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── GET /payments/{orderId} ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /payments/{orderId} – returns payment for own order (200)")
    void getPayment_ownOrder_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(paymentService.getPaymentByOrderId(USER_ID, 10L)).thenReturn(paymentResp);

        mockMvc.perform(get("/api/v1/payments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(10))
                .andExpect(jsonPath("$.transactionRef").value("TXN-ABC"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /payments/{orderId} – order not found returns 404")
    void getPayment_orderNotFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(paymentService.getPaymentByOrderId(USER_ID, 99L))
                .thenThrow(new ResourceNotFoundException("Order not found: 99"));

        mockMvc.perform(get("/api/v1/payments/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /payments/{orderId} – unauthenticated is rejected (4xx)")
    void getPayment_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payments/10"))
                .andExpect(status().is4xxClientError());
    }
}
