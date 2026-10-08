package com.bookworm.repository;

import com.bookworm.entity.GiftPointsTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GiftPointsTransactionRepository extends JpaRepository<GiftPointsTransaction, Long> {
    Page<GiftPointsTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
