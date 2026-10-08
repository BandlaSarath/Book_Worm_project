package com.bookworm.service;

import com.bookworm.entity.Order;
import com.bookworm.entity.OrderItem;
import com.bookworm.entity.User;
import com.bookworm.enums.OrderStatus;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService unit tests")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CartRepository cartRepository;
    @Mock private GiftPointsRepository giftPointsRepository;
    @Mock private GiftPointsTransactionRepository giftPointsTxRepository;
    @Mock private BookWormMapper mapper;
    @Mock private CartService cartService;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Order order48hAgo;
    private Order orderOld;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).email("alice@example.com")
                .role(UserRole.CUSTOMER).build();

        order48hAgo = Order.builder()
                .id(10L).user(user)
                .status(OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN)
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .items(new ArrayList<>())
                .build();
        // Set placedAt within 48 hours
        order48hAgo.setPlacedAt(LocalDateTime.now().minusHours(10));

        orderOld = Order.builder()
                .id(20L).user(user)
                .status(OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN)
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .items(new ArrayList<>())
                .build();
        orderOld.setPlacedAt(LocalDateTime.now().minusHours(50));
    }

    @Test
    @DisplayName("cancelOrder – within 48 hours succeeds")
    void cancelOrder_within48Hours_succeeds() {
        when(orderRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(order48hAgo));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toOrderResponse(any())).thenReturn(null);

        orderService.cancelOrder(1L, 10L);

        assertThat(order48hAgo.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order48hAgo.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelOrder – beyond 48 hours throws BusinessException")
    void cancelOrder_beyond48Hours_throws() {
        when(orderRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(orderOld));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("48-hour");
    }

    @Test
    @DisplayName("cancelOrder – already cancelled throws BusinessException")
    void cancelOrder_alreadyCancelled_throws() {
        order48hAgo.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(order48hAgo));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getOrder – not found throws ResourceNotFoundException")
    void getOrder_notFound_throws() {
        when(orderRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── D: 48-hour boundary condition ──────────────────────────────────────────

    @Test
    @DisplayName("isCancellationEligible – order placed exactly 48 hours ago is NOT eligible")
    void isCancellationEligible_exactlyAt48Hours_notEligible() {
        // placedAt.isAfter(now - 48h) is false when exactly equal → not eligible
        Order atBoundary = Order.builder()
                .id(30L).user(user)
                .status(OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN)
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .items(new ArrayList<>())
                .build();
        atBoundary.setPlacedAt(LocalDateTime.now().minusHours(48));

        assertThat(atBoundary.isCancellationEligible()).isFalse();
    }

    @Test
    @DisplayName("isCancellationEligible – order placed 47h 59m ago is still eligible")
    void isCancellationEligible_justUnder48Hours_eligible() {
        Order justUnder = Order.builder()
                .id(31L).user(user)
                .status(OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN)
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .items(new ArrayList<>())
                .build();
        justUnder.setPlacedAt(LocalDateTime.now().minusMinutes(47 * 60 + 59));

        assertThat(justUnder.isCancellationEligible()).isTrue();
    }

    @Test
    @DisplayName("cancelOrder – another user's order throws ResourceNotFoundException")
    void cancelOrder_anotherUsersOrder_throws() {
        when(orderRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(2L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
