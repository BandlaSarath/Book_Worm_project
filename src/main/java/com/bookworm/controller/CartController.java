package com.bookworm.controller;

import com.bookworm.dto.request.AddToCartRequest;
import com.bookworm.dto.request.UpdateCartItemRequest;
import com.bookworm.dto.response.CartResponse;
import com.bookworm.service.CartService;
import com.bookworm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController extends BaseController {

    private final CartService cartService;

    public CartController(CartService cartService, UserService userService) {
        super(userService);
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication auth) {
        return ResponseEntity.ok(cartService.getCart(currentUser(auth).getId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(Authentication auth,
                                                 @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addToCart(currentUser(auth).getId(), request));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(Authentication auth,
                                                    @PathVariable Long itemId,
                                                    @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateCartItem(currentUser(auth).getId(), itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(Authentication auth, @PathVariable Long itemId) {
        return ResponseEntity.ok(cartService.removeCartItem(currentUser(auth).getId(), itemId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication auth) {
        cartService.clearCart(currentUser(auth).getId());
        return ResponseEntity.noContent().build();
    }
}
