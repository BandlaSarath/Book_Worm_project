package com.bookworm.service;

import com.bookworm.dto.request.AddToCartRequest;
import com.bookworm.dto.request.UpdateCartItemRequest;
import com.bookworm.dto.response.CartResponse;
import com.bookworm.entity.Book;
import com.bookworm.entity.Cart;
import com.bookworm.entity.CartItem;
import com.bookworm.enums.BookAvailability;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.BookRepository;
import com.bookworm.repository.CartItemRepository;
import com.bookworm.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService unit tests")
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private BookRepository bookRepository;

    private final BookWormMapper mapper = new BookWormMapper();
    private CartService cartService;

    private static final Long USER_ID = 1L;
    private static final Long BOOK_ID = 10L;

    private Book inStockBook;
    private Cart emptyCart;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, bookRepository, mapper);

        inStockBook = Book.builder()
                .id(BOOK_ID)
                .title("Clean Code")
                .price(new BigDecimal("29.99"))
                .stock(5)
                .availability(BookAvailability.IN_STOCK)
                .authors(new ArrayList<>())
                .categories(new ArrayList<>())
                .build();

        emptyCart = Cart.builder().id(100L).items(new ArrayList<>()).build();
    }

    // ── getCart ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCart – existing cart is returned with empty items list")
    void getCart_existingCart_returnsEmptyCartResponse() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));

        CartResponse result = cartService.getCart(USER_ID);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getItemCount()).isZero();
    }

    @Test
    @DisplayName("getCart – creates new cart when none exists")
    void getCart_noExistingCart_createsNewCart() {
        Cart newCart = Cart.builder().id(200L).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(newCart);

        CartResponse result = cartService.getCart(USER_ID);

        assertThat(result.getId()).isEqualTo(200L);
        verify(cartRepository).save(any(Cart.class));
    }

    // ── addToCart ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addToCart – adds new item and returns updated cart with correct subtotal")
    void addToCart_newItem_returnsCartWithSubtotal() {
        AddToCartRequest req = new AddToCartRequest();
        req.setBookId(BOOK_ID);
        req.setQuantity(2);

        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(inStockBook));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));
        when(cartItemRepository.findByCartIdAndBookId(emptyCart.getId(), BOOK_ID))
                .thenReturn(Optional.empty());

        // After save, cart now contains the new item
        Cart savedCart = Cart.builder().id(100L).items(new ArrayList<>()).build();
        CartItem savedItem = CartItem.builder()
                .id(1L).book(inStockBook).quantity(2)
                .priceSnapshot(new BigDecimal("29.99")).cart(savedCart).build();
        savedCart.getItems().add(savedItem);
        when(cartRepository.save(emptyCart)).thenReturn(savedCart);

        CartResponse result = cartService.addToCart(USER_ID, req);

        assertThat(result.getItemCount()).isEqualTo(1);
        assertThat(result.getSubtotal()).isEqualByComparingTo(new BigDecimal("59.98"));
    }

    @Test
    @DisplayName("addToCart – increments quantity when book already in cart")
    void addToCart_existingItem_incrementsQuantity() {
        AddToCartRequest req = new AddToCartRequest();
        req.setBookId(BOOK_ID);
        req.setQuantity(1);

        CartItem existingItem = CartItem.builder()
                .id(1L).book(inStockBook).quantity(2)
                .priceSnapshot(new BigDecimal("29.99")).cart(emptyCart).build();

        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(inStockBook));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));
        when(cartItemRepository.findByCartIdAndBookId(emptyCart.getId(), BOOK_ID))
                .thenReturn(Optional.of(existingItem));
        when(cartItemRepository.save(existingItem)).thenReturn(existingItem);

        Cart savedCart = Cart.builder().id(100L).items(List.of(existingItem)).build();
        existingItem.setCart(savedCart);
        when(cartRepository.save(emptyCart)).thenReturn(savedCart);

        CartResponse result = cartService.addToCart(USER_ID, req);

        // quantity updated to 3, subtotal = 3 * 29.99
        assertThat(existingItem.getQuantity()).isEqualTo(3);
        assertThat(result.getSubtotal()).isEqualByComparingTo(new BigDecimal("89.97"));
    }

    @Test
    @DisplayName("addToCart – throws ResourceNotFoundException for nonexistent book")
    void addToCart_bookNotFound_throwsException() {
        AddToCartRequest req = new AddToCartRequest();
        req.setBookId(99L);
        req.setQuantity(1);

        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addToCart(USER_ID, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("addToCart – throws BusinessException when book is out of stock")
    void addToCart_outOfStock_throwsException() {
        Book outOfStock = Book.builder()
                .id(BOOK_ID).title("Rare Book").price(new BigDecimal("9.99"))
                .stock(0).availability(BookAvailability.OUT_OF_STOCK)
                .authors(new ArrayList<>()).categories(new ArrayList<>()).build();

        AddToCartRequest req = new AddToCartRequest();
        req.setBookId(BOOK_ID);
        req.setQuantity(1);

        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(outOfStock));

        assertThatThrownBy(() -> cartService.addToCart(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("addToCart – throws BusinessException when requested quantity exceeds stock")
    void addToCart_quantityExceedsStock_throwsException() {
        AddToCartRequest req = new AddToCartRequest();
        req.setBookId(BOOK_ID);
        req.setQuantity(10); // stock is only 5

        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(inStockBook));

        assertThatThrownBy(() -> cartService.addToCart(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");
    }

    // ── updateCartItem ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCartItem – updates quantity and returns refreshed cart")
    void updateCartItem_validRequest_updatesQuantity() {
        CartItem item = CartItem.builder()
                .id(1L).book(inStockBook).quantity(1)
                .priceSnapshot(new BigDecimal("29.99")).cart(emptyCart).build();

        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(3);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(cartItemRepository.save(item)).thenReturn(item);

        Cart refreshedCart = Cart.builder().id(100L).items(List.of(item)).build();
        item.setCart(refreshedCart);
        when(cartRepository.findById(100L)).thenReturn(Optional.of(refreshedCart));

        CartResponse result = cartService.updateCartItem(USER_ID, 1L, req);

        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(result.getSubtotal()).isEqualByComparingTo(new BigDecimal("89.97"));
    }

    @Test
    @DisplayName("updateCartItem – throws ResourceNotFoundException when item does not belong to user's cart")
    void updateCartItem_itemNotInUserCart_throwsException() {
        Cart otherCart = Cart.builder().id(999L).items(new ArrayList<>()).build();
        CartItem item = CartItem.builder()
                .id(1L).book(inStockBook).quantity(1)
                .priceSnapshot(new BigDecimal("29.99")).cart(otherCart).build();

        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(2);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        // item.getCart().getId() == 999, emptyCart.getId() == 100 → filter fails
        assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, 1L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart item not found");
    }

    @Test
    @DisplayName("updateCartItem – throws BusinessException when new quantity exceeds stock")
    void updateCartItem_quantityExceedsStock_throwsException() {
        CartItem item = CartItem.builder()
                .id(1L).book(inStockBook).quantity(1)
                .priceSnapshot(new BigDecimal("29.99")).cart(emptyCart).build();

        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(10); // stock is 5

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, 1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");
    }

    // ── removeCartItem ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeCartItem – removes item and returns updated cart")
    void removeCartItem_validItem_removesAndReturnsCart() {
        CartItem item = CartItem.builder()
                .id(1L).book(inStockBook).quantity(2)
                .priceSnapshot(new BigDecimal("29.99")).cart(emptyCart).build();
        emptyCart.getItems().add(item);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(item));

        Cart savedCart = Cart.builder().id(100L).items(new ArrayList<>()).build();
        when(cartRepository.save(emptyCart)).thenReturn(savedCart);

        CartResponse result = cartService.removeCartItem(USER_ID, 1L);

        verify(cartItemRepository).delete(item);
        assertThat(result.getItemCount()).isZero();
        assertThat(result.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("removeCartItem – throws ResourceNotFoundException when item not in user cart")
    void removeCartItem_itemNotInUserCart_throwsException() {
        Cart otherCart = Cart.builder().id(999L).items(new ArrayList<>()).build();
        CartItem item = CartItem.builder()
                .id(5L).book(inStockBook).quantity(1)
                .priceSnapshot(new BigDecimal("29.99")).cart(otherCart).build();

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.removeCartItem(USER_ID, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart item not found");
    }

    // ── clearCart ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearCart – empties items list and saves")
    void clearCart_removesAllItems() {
        CartItem item = CartItem.builder()
                .id(1L).book(inStockBook).quantity(1)
                .priceSnapshot(new BigDecimal("29.99")).cart(emptyCart).build();
        emptyCart.getItems().add(item);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));
        when(cartRepository.save(emptyCart)).thenReturn(emptyCart);

        cartService.clearCart(USER_ID);

        assertThat(emptyCart.getItems()).isEmpty();
        verify(cartRepository).save(emptyCart);
    }
}
