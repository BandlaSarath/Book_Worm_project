package com.bookworm.controller;

import com.bookworm.dto.request.AddToWishlistRequest;
import com.bookworm.dto.response.WishlistResponse;
import com.bookworm.service.UserService;
import com.bookworm.service.WishlistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController extends BaseController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService, UserService userService) {
        super(userService);
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public ResponseEntity<WishlistResponse> getWishlist(Authentication auth) {
        return ResponseEntity.ok(wishlistService.getWishlist(currentUser(auth).getId()));
    }

    @PostMapping
    public ResponseEntity<WishlistResponse> addToWishlist(Authentication auth,
                                                           @Valid @RequestBody AddToWishlistRequest request) {
        return ResponseEntity.ok(wishlistService.addToWishlist(currentUser(auth), request));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> removeFromWishlist(Authentication auth, @PathVariable Long itemId) {
        wishlistService.removeFromWishlist(currentUser(auth).getId(), itemId);
        return ResponseEntity.noContent().build();
    }
}
