package com.bookworm.service;

import com.bookworm.dto.response.BuyAgainResponse;
import com.bookworm.entity.*;
import com.bookworm.enums.BookAvailability;
import com.bookworm.enums.OrderStatus;
import com.bookworm.enums.UserRole;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bookworm.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService – buyAgain unit tests")
class OrderServiceBuyAgainTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CartRepository cartRepository;
    @Mock private GiftPointsRepository giftPointsRepository;
    @Mock private GiftPointsTransactionRepository giftPointsTxRepository;
    @Mock private CartService cartService;

    private final BookWormMapper mapper = new BookWormMapper();
    private OrderService orderService;

    private static final Long USER_ID  = 1L;
    private static final Long ORDER_ID = 10L;

    private User user;
    private Cart emptyCart;
    private Book inStockBook;
    private Book outOfStockBook;
    private Book zeroStockBook;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, addressRepository, cartRepository,
                giftPointsRepository, giftPointsTxRepository, mapper, cartService);

        user = User.builder().id(USER_ID).email("alice@example.com")
                .role(UserRole.CUSTOMER).orders(new ArrayList<>()).build();

        emptyCart = Cart.builder().id(100L).user(user).items(new ArrayList<>()).build();

        inStockBook = Book.builder().id(20L).title("Clean Code")
                .price(new BigDecimal("29.99")).stock(5)
                .availability(BookAvailability.IN_STOCK)
                .authors(new ArrayList<>()).categories(new ArrayList<>()).build();

        outOfStockBook = Book.builder().id(21L).title("Rare Book")
                .price(new BigDecimal("9.99")).stock(0)
                .availability(BookAvailability.OUT_OF_STOCK)
                .authors(new ArrayList<>()).categories(new ArrayList<>()).build();

        zeroStockBook = Book.builder().id(22L).title("Depleted Book")
                .price(new BigDecimal("14.99")).stock(0)
                .availability(BookAvailability.IN_STOCK) // marked in-stock but stock=0
                .authors(new ArrayList<>()).categories(new ArrayList<>()).build();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Order orderWith(OrderItem... items) {
        Order order = Order.builder()
                .id(ORDER_ID).user(user).status(OrderStatus.CONFIRMED)
                .subtotal(BigDecimal.TEN).giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN).items(new ArrayList<>(List.of(items))).build();
        order.setPlacedAt(LocalDateTime.now().minusDays(3));
        return order;
    }

    private OrderItem itemFor(Book book, int qty) {
        return OrderItem.builder().book(book).quantity(qty)
                .priceAtPurchase(book.getPrice()).build();
    }

    // ── item 5: eligible books are added to cart ───────────────────────────────

    @Test
    @DisplayName("buyAgain – in-stock books are added to user cart")
    void buyAgain_inStockBook_addedToCart() {
        Order order = orderWith(itemFor(inStockBook, 2));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartService.getOrCreateCartForUser(user)).thenReturn(emptyCart);
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        BuyAgainResponse result = orderService.buyAgain(user, ORDER_ID);

        assertThat(result.getCart().getItemCount()).isEqualTo(1);
        assertThat(result.getSkippedItems()).isEmpty();
        // cart must have the book with the original order quantity
        assertThat(emptyCart.getItems()).hasSize(1);
        assertThat(emptyCart.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(emptyCart.getItems().get(0).getBook().getId()).isEqualTo(20L);
    }

    // ── item 6: unavailable books are skipped safely ──────────────────────────

    @Test
    @DisplayName("buyAgain – OUT_OF_STOCK book goes to skippedItems, not cart")
    void buyAgain_outOfStockBook_isSkipped() {
        Order order = orderWith(itemFor(outOfStockBook, 1));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartService.getOrCreateCartForUser(user)).thenReturn(emptyCart);
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        BuyAgainResponse result = orderService.buyAgain(user, ORDER_ID);

        assertThat(result.getSkippedItems()).hasSize(1);
        assertThat(result.getSkippedItems().get(0).getId()).isEqualTo(21L);
        assertThat(emptyCart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("buyAgain – book with stock=0 (any availability) goes to skippedItems")
    void buyAgain_zeroStockBook_isSkipped() {
        Order order = orderWith(itemFor(zeroStockBook, 1));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartService.getOrCreateCartForUser(user)).thenReturn(emptyCart);
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        BuyAgainResponse result = orderService.buyAgain(user, ORDER_ID);

        assertThat(result.getSkippedItems()).hasSize(1);
        assertThat(result.getSkippedItems().get(0).getId()).isEqualTo(22L);
        assertThat(emptyCart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("buyAgain – mixed order: eligible books added, unavailable books skipped")
    void buyAgain_mixedAvailability_separatesCorrectly() {
        Order order = orderWith(itemFor(inStockBook, 1), itemFor(outOfStockBook, 1));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartService.getOrCreateCartForUser(user)).thenReturn(emptyCart);
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        BuyAgainResponse result = orderService.buyAgain(user, ORDER_ID);

        assertThat(emptyCart.getItems()).hasSize(1);
        assertThat(result.getSkippedItems()).hasSize(1);
        assertThat(result.getSkippedItems().get(0).getId()).isEqualTo(21L);
    }

    // ── item 7: no new Order is created ───────────────────────────────────────

    @Test
    @DisplayName("buyAgain – never calls orderRepository.save (no new order created)")
    void buyAgain_doesNotCreateNewOrder() {
        Order order = orderWith(itemFor(inStockBook, 1));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartService.getOrCreateCartForUser(user)).thenReturn(emptyCart);
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        orderService.buyAgain(user, ORDER_ID);

        // orderRepository.save must never be called — only the read is allowed
        verify(orderRepository, never()).save(any());
    }

    // ── item 8: no Payment record is created ──────────────────────────────────

    @Test
    @DisplayName("buyAgain – no payment repository interaction (no payment created)")
    void buyAgain_doesNotCreatePayment() {
        Order order = orderWith(itemFor(inStockBook, 1));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartService.getOrCreateCartForUser(user)).thenReturn(emptyCart);
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        orderService.buyAgain(user, ORDER_ID);

        // giftPointsTxRepository and giftPointsRepository must also be untouched
        verifyNoInteractions(giftPointsTxRepository, giftPointsRepository);
    }

    // ── item 9: cart quantity merging ─────────────────────────────────────────

    @Test
    @DisplayName("buyAgain – increments existing cart item quantity instead of duplicating")
    void buyAgain_existingCartItem_incrementsQuantity() {
        // Cart already has 1 copy of inStockBook
        CartItem existing = CartItem.builder()
                .id(1L).cart(emptyCart).book(inStockBook)
                .quantity(1).priceSnapshot(new BigDecimal("29.99")).build();
        emptyCart.getItems().add(existing);

        Order order = orderWith(itemFor(inStockBook, 2));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartService.getOrCreateCartForUser(user)).thenReturn(emptyCart);
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        orderService.buyAgain(user, ORDER_ID);

        // Still 1 CartItem, quantity = 1 + 2 = 3
        assertThat(emptyCart.getItems()).hasSize(1);
        assertThat(emptyCart.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("buyAgain – new cart item uses current book price as priceSnapshot")
    void buyAgain_newItem_usesCurrentPrice() {
        Order order = orderWith(itemFor(inStockBook, 1));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartService.getOrCreateCartForUser(user)).thenReturn(emptyCart);
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        orderService.buyAgain(user, ORDER_ID);

        assertThat(emptyCart.getItems().get(0).getPriceSnapshot())
                .isEqualByComparingTo(inStockBook.getPrice());
    }

    // ── cross-user isolation ───────────────────────────────────────────────────

    @Test
    @DisplayName("buyAgain – another user's order throws ResourceNotFoundException")
    void buyAgain_otherUsersOrder_throws() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.buyAgain(user, ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(ORDER_ID));
    }
}
