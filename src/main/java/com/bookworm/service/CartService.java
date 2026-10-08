package com.bookworm.service;

import com.bookworm.dto.request.AddToCartRequest;
import com.bookworm.dto.request.UpdateCartItemRequest;
import com.bookworm.dto.response.CartResponse;
import com.bookworm.entity.Book;
import com.bookworm.entity.Cart;
import com.bookworm.entity.CartItem;
import com.bookworm.entity.User;
import com.bookworm.enums.BookAvailability;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.BookRepository;
import com.bookworm.repository.CartItemRepository;
import com.bookworm.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BookRepository bookRepository;
    private final BookWormMapper mapper;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       BookRepository bookRepository,
                       BookWormMapper mapper) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.bookRepository = bookRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return mapper.toCartResponse(cart);
    }

    @Transactional
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + request.getBookId()));

        if (book.getAvailability() == BookAvailability.OUT_OF_STOCK || book.getStock() < request.getQuantity()) {
            throw new BusinessException("Insufficient stock for book: " + book.getTitle());
        }

        Cart cart = getOrCreateCart(userId);

        Optional<CartItem> existing = cartItemRepository.findByCartIdAndBookId(cart.getId(), book.getId());
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + request.getQuantity();
            if (book.getStock() < newQty) {
                throw new BusinessException("Insufficient stock");
            }
            item.setQuantity(newQty);
            cartItemRepository.save(item);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .book(book)
                    .quantity(request.getQuantity())
                    .priceSnapshot(book.getPrice())
                    .build();
            cart.getItems().add(item);
        }

        return mapper.toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateCartItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getCart().getId().equals(cart.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

        if (item.getBook().getStock() < request.getQuantity()) {
            throw new BusinessException("Insufficient stock");
        }

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return mapper.toCartResponse(cartRepository.findById(cart.getId()).orElseThrow());
    }

    @Transactional
    public CartResponse removeCartItem(Long userId, Long itemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getCart().getId().equals(cart.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        return mapper.toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            // Construct a User proxy with just the ID for the FK reference
            User userRef = User.builder().id(userId).build();
            Cart newCart = Cart.builder().user(userRef).build();
            return cartRepository.save(newCart);
        });
    }

    /** Creates or retrieves cart tied to an authenticated user entity */
    public Cart getOrCreateCartForUser(com.bookworm.entity.User user) {
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart newCart = Cart.builder().user(user).build();
            return cartRepository.save(newCart);
        });
    }
}
