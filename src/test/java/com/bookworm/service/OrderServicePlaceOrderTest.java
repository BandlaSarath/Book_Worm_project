package com.bookworm.service;

import com.bookworm.dto.request.PlaceOrderRequest;
import com.bookworm.dto.response.OrderResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.entity.*;
import com.bookworm.enums.BookAvailability;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService – placeOrder / listOrders / getOrder unit tests")
class OrderServicePlaceOrderTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CartRepository cartRepository;
    @Mock private GiftPointsRepository giftPointsRepository;
    @Mock private GiftPointsTransactionRepository giftPointsTxRepository;
    @Mock private CartService cartService;

    // Real mapper — avoids NPE surprises in response mapping
    private final BookWormMapper mapper = new BookWormMapper();
    private OrderService orderService;

    private static final Long USER_ID    = 1L;
    private static final Long ADDRESS_ID = 5L;
    private static final Long ORDER_ID   = 10L;
    private static final Long OTHER_UID  = 99L;

    private User user;
    private Address address;
    private Book book;
    private Cart cartWithItem;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, addressRepository, cartRepository,
                giftPointsRepository, giftPointsTxRepository, mapper, cartService);

        user = User.builder().id(USER_ID).email("alice@example.com")
                .role(UserRole.CUSTOMER).orders(new ArrayList<>()).build();

        address = Address.builder().id(ADDRESS_ID).user(user)
                .line1("123 Main St").city("Springfield").state("IL")
                .postalCode("62701").country("US").isDefault(true).build();

        book = Book.builder().id(20L).title("Clean Code")
                .price(new BigDecimal("29.99")).stock(10)
                .availability(BookAvailability.IN_STOCK)
                .authors(new ArrayList<>()).categories(new ArrayList<>()).build();

        cartWithItem = Cart.builder().id(100L).user(user).items(new ArrayList<>()).build();
        cartItem = CartItem.builder().id(1L).cart(cartWithItem).book(book)
                .quantity(2).priceSnapshot(new BigDecimal("29.99")).build();
        cartWithItem.getItems().add(cartItem);
    }

    // ── placeOrder: happy path ─────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder – creates order, copies cart items, clears cart, returns PENDING")
    void placeOrder_validCart_createsOrderAndClearsCart() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId(ADDRESS_ID);
        req.setGiftPointsToRedeem(BigDecimal.ZERO);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cartWithItem));
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));

        Order savedOrder = Order.builder()
                .id(ORDER_ID).user(user).deliveryAddress(address)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("59.98"))
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("59.98"))
                .items(List.of(OrderItem.builder().id(1L).book(book).quantity(2)
                        .priceAtPurchase(new BigDecimal("29.99")).build()))
                .build();
        savedOrder.setPlacedAt(LocalDateTime.now());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(cartRepository.save(cartWithItem)).thenReturn(cartWithItem);

        OrderResponse result = orderService.placeOrder(user, req);

        assertThat(result.getId()).isEqualTo(ORDER_ID);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getSubtotal()).isEqualByComparingTo("59.98");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("59.98");
        // cart must be cleared
        assertThat(cartWithItem.getItems()).isEmpty();
        verify(orderRepository).save(any(Order.class));
        verify(cartRepository).save(cartWithItem);
    }

    @Test
    @DisplayName("placeOrder – order items preserve quantity from cart")
    void placeOrder_orderItemsPreserveQuantity() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId(ADDRESS_ID);
        req.setGiftPointsToRedeem(BigDecimal.ZERO);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cartWithItem));
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));

        // Capture the order passed to save and inspect its items
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            o.setPlacedAt(LocalDateTime.now());
            return o;
        });
        when(cartRepository.save(any())).thenReturn(cartWithItem);

        OrderResponse result = orderService.placeOrder(user, req);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(result.getItems().get(0).getPriceAtPurchase())
                .isEqualByComparingTo("29.99");
    }

    @Test
    @DisplayName("placeOrder – total = subtotal - gift points (floor zero)")
    void placeOrder_withGiftPoints_deductsFromTotal() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId(ADDRESS_ID);
        req.setGiftPointsToRedeem(new BigDecimal("10.00"));

        GiftPoints gp = GiftPoints.builder().user(user).balance(new BigDecimal("50.00")).build();

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cartWithItem));
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));
        when(giftPointsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(gp));
        when(giftPointsRepository.save(gp)).thenReturn(gp);
        when(giftPointsTxRepository.save(any())).thenReturn(null);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            o.setPlacedAt(LocalDateTime.now());
            return o;
        });
        when(cartRepository.save(any())).thenReturn(cartWithItem);

        OrderResponse result = orderService.placeOrder(user, req);

        // subtotal = 2 * 29.99 = 59.98; minus 10.00 = 49.98
        assertThat(result.getTotalAmount()).isEqualByComparingTo("49.98");
        assertThat(result.getGiftPointsRedeemed()).isEqualByComparingTo("10.00");
        // gift points balance must be decremented
        assertThat(gp.getBalance()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("placeOrder – insufficient gift points balance throws BusinessException")
    void placeOrder_insufficientGiftPoints_throws() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId(ADDRESS_ID);
        req.setGiftPointsToRedeem(new BigDecimal("100.00"));

        GiftPoints gp = GiftPoints.builder().user(user).balance(new BigDecimal("5.00")).build();

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cartWithItem));
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));
        when(giftPointsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(gp));

        assertThatThrownBy(() -> orderService.placeOrder(user, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gift point");
    }

    // ── placeOrder: empty cart rejection ──────────────────────────────────────

    @Test
    @DisplayName("placeOrder – no cart at all throws BusinessException")
    void placeOrder_cartNotFound_throws() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId(ADDRESS_ID);
        req.setGiftPointsToRedeem(BigDecimal.ZERO);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(user, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("placeOrder – empty cart throws BusinessException")
    void placeOrder_emptyCart_throws() {
        Cart emptyCart = Cart.builder().id(100L).user(user).items(new ArrayList<>()).build();
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId(ADDRESS_ID);
        req.setGiftPointsToRedeem(BigDecimal.ZERO);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));

        assertThatThrownBy(() -> orderService.placeOrder(user, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty");
    }

    // ── placeOrder: address validation ────────────────────────────────────────

    @Test
    @DisplayName("placeOrder – address not owned by user throws ResourceNotFoundException")
    void placeOrder_addressNotFound_throws() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId(999L);
        req.setGiftPointsToRedeem(BigDecimal.ZERO);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cartWithItem));
        when(addressRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(user, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Address not found");
    }

    // ── listOrders ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listOrders – returns paged order history for user")
    void listOrders_returnsUserOrders() {
        Order order = Order.builder()
                .id(ORDER_ID).user(user).status(OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN).giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN).items(new ArrayList<>()).build();
        order.setPlacedAt(LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 20);
        Page<Order> orderPage = new PageImpl<>(List.of(order));
        when(orderRepository.findByUserId(USER_ID, pageable)).thenReturn(orderPage);

        PagedResponse<OrderResponse> result = orderService.listOrders(USER_ID, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("listOrders – filters by status when status parameter supplied")
    void listOrders_withStatusFilter_usesStatusQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findByUserIdAndStatus(USER_ID, OrderStatus.PENDING, pageable))
                .thenReturn(Page.empty());

        PagedResponse<OrderResponse> result =
                orderService.listOrders(USER_ID, OrderStatus.PENDING, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(orderRepository).findByUserIdAndStatus(USER_ID, OrderStatus.PENDING, pageable);
        verify(orderRepository, never()).findByUserId(any(), any());
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrder – returns order when it belongs to user")
    void getOrder_ownedOrder_returnsResponse() {
        Order order = Order.builder()
                .id(ORDER_ID).user(user).status(OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN).giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN).items(new ArrayList<>()).build();
        order.setPlacedAt(LocalDateTime.now());

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrder(USER_ID, ORDER_ID);

        assertThat(result.getId()).isEqualTo(ORDER_ID);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("getOrder – another user cannot access the order (ResourceNotFoundException)")
    void getOrder_otherUsersOrder_throws() {
        // findByIdAndUserId with OTHER_UID returns empty — user isolation enforced by query
        when(orderRepository.findByIdAndUserId(ORDER_ID, OTHER_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(OTHER_UID, ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(ORDER_ID));
    }
}
