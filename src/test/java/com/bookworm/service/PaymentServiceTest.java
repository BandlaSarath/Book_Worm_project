package com.bookworm.service;

import com.bookworm.entity.Payment;
import com.bookworm.entity.Order;
import com.bookworm.entity.User;
import com.bookworm.enums.OrderStatus;
import com.bookworm.enums.PaymentMethod;
import com.bookworm.enums.PaymentStatus;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.BusinessException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.OrderRepository;
import com.bookworm.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService unit tests")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BookWormMapper mapper;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("alice@example.com").role(UserRole.CUSTOMER).build();
        pendingOrder = Order.builder()
                .id(5L).user(user)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("29.99"))
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("29.99"))
                .items(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("completePayment SUCCESS – order becomes CONFIRMED")
    void completePayment_success_confirmsOrder() {
        com.bookworm.dto.request.PaymentCompleteRequest req = new com.bookworm.dto.request.PaymentCompleteRequest();
        req.setOrderId(5L);
        req.setTransactionRef("TXN-ABC");
        req.setMockResult("SUCCESS");

        Payment payment = Payment.builder()
                .id(1L).order(pendingOrder)
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PENDING)
                .transactionRef("TXN-ABC")
                .amount(new BigDecimal("29.99"))
                .build();

        when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.findByTransactionRef("TXN-ABC")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toOrderResponse(any())).thenReturn(null);
        when(mapper.toPaymentResponse(any())).thenReturn(null);

        var result = paymentService.completePayment(1L, req);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getMessage()).contains("confirmed");
    }

    @Test
    @DisplayName("completePayment FAILED – order stays PENDING")
    void completePayment_failed_orderRemainsUnchanged() {
        com.bookworm.dto.request.PaymentCompleteRequest req = new com.bookworm.dto.request.PaymentCompleteRequest();
        req.setOrderId(5L);
        req.setTransactionRef("TXN-XYZ");
        req.setMockResult("FAILED");

        Payment payment = Payment.builder()
                .id(2L).order(pendingOrder)
                .method(PaymentMethod.UPI)
                .status(PaymentStatus.PENDING)
                .transactionRef("TXN-XYZ")
                .amount(new BigDecimal("29.99"))
                .build();

        when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.findByTransactionRef("TXN-XYZ")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toOrderResponse(any())).thenReturn(null);
        when(mapper.toPaymentResponse(any())).thenReturn(null);

        var result = paymentService.completePayment(1L, req);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getMessage()).contains("failed");
    }

    @Test
    @DisplayName("completePayment – mismatched transactionRef throws BusinessException")
    void completePayment_mismatchedRef_throws() {
        com.bookworm.dto.request.PaymentCompleteRequest req = new com.bookworm.dto.request.PaymentCompleteRequest();
        req.setOrderId(5L);
        req.setTransactionRef("TXN-WRONG");
        req.setMockResult("SUCCESS");

        Order otherOrder = Order.builder().id(99L).user(user).status(OrderStatus.PENDING)
                .items(new ArrayList<>()).subtotal(BigDecimal.ONE)
                .giftPointsRedeemed(BigDecimal.ZERO).totalAmount(BigDecimal.ONE).build();
        Payment payment = Payment.builder()
                .id(3L).order(otherOrder)
                .status(PaymentStatus.PENDING)
                .transactionRef("TXN-WRONG")
                .amount(BigDecimal.ONE).build();

        when(orderRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.findByTransactionRef("TXN-WRONG")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.completePayment(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match");
    }
}
