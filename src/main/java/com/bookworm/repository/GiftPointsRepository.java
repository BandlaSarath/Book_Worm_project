package com.bookworm.repository;

import com.bookworm.entity.GiftPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GiftPointsRepository extends JpaRepository<GiftPoints, Long> {
    Optional<GiftPoints> findByUserId(Long userId);
}
