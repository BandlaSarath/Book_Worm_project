package com.bookworm.repository;

import com.bookworm.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByUserId(Long userId);
    Optional<WishlistItem> findByUserIdAndBookId(Long userId, Long bookId);
    Optional<WishlistItem> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndBookId(Long userId, Long bookId);
}
