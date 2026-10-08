package com.bookworm.service;

import com.bookworm.dto.request.AddToWishlistRequest;
import com.bookworm.dto.response.WishlistResponse;
import com.bookworm.entity.Book;
import com.bookworm.entity.User;
import com.bookworm.entity.WishlistItem;
import com.bookworm.exception.DuplicateResourceException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.BookRepository;
import com.bookworm.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final BookRepository bookRepository;
    private final BookWormMapper mapper;

    public WishlistService(WishlistRepository wishlistRepository,
                            BookRepository bookRepository,
                            BookWormMapper mapper) {
        this.wishlistRepository = wishlistRepository;
        this.bookRepository = bookRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public WishlistResponse getWishlist(Long userId) {
        List<WishlistItem> items = wishlistRepository.findByUserId(userId);
        return WishlistResponse.builder()
                .items(items.stream().map(mapper::toWishlistItemResponse).collect(Collectors.toList()))
                .total(items.size())
                .build();
    }

    @Transactional
    public WishlistResponse addToWishlist(User user, AddToWishlistRequest request) {
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + request.getBookId()));

        if (wishlistRepository.existsByUserIdAndBookId(user.getId(), book.getId())) {
            throw new DuplicateResourceException("Book already in wishlist");
        }

        WishlistItem item = WishlistItem.builder().user(user).book(book).build();
        wishlistRepository.save(item);
        return getWishlist(user.getId());
    }

    @Transactional
    public void removeFromWishlist(Long userId, Long itemId) {
        WishlistItem item = wishlistRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found: " + itemId));
        wishlistRepository.delete(item);
    }
}
