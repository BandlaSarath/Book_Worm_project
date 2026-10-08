package com.bookworm.repository;

import com.bookworm.entity.Order;
import com.bookworm.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
}
