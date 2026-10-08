package com.bookworm.service;

import com.bookworm.dto.request.RedeemGiftPointsRequest;
import com.bookworm.dto.response.GiftPointTransactionResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.dto.response.RedeemGiftPointsResponse;
import com.bookworm.entity.GiftPoints;
import com.bookworm.entity.GiftPointsTransaction;
import com.bookworm.entity.Order;
import com.bookworm.entity.User;
import com.bookworm.enums.OrderStatus;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.GiftPointsRepository;
import com.bookworm.repository.GiftPointsTransactionRepository;
import com.bookworm.repository.OrderRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class GiftPointsService {

    private final GiftPointsRepository giftPointsRepository;
    private final GiftPointsTransactionRepository txRepository;
    private final OrderRepository orderRepository;
    private final BookWormMapper mapper;

    public GiftPointsService(GiftPointsRepository giftPointsRepository,
                              GiftPointsTransactionRepository txRepository,
                              OrderRepository orderRepository,
                              BookWormMapper mapper) {
        this.giftPointsRepository = giftPointsRepository;
        this.txRepository = txRepository;
        this.orderRepository = orderRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId) {
        return giftPointsRepository.findByUserId(userId)
                .map(GiftPoints::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public PagedResponse<GiftPointTransactionResponse> getTransactions(Long userId, Pageable pageable) {
        return mapper.toPagedResponse(
                txRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                        .map(mapper::toGiftPointTransactionResponse));
    }

    @Transactional
    public RedeemGiftPointsResponse redeem(User user, RedeemGiftPointsRequest request) {
        Order order = orderRepository.findByIdAndUserId(request.getOrderId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Gift points can only be redeemed for PENDING orders");
        }

        GiftPoints gp = giftPointsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("Gift points account not found"));

        BigDecimal amount = request.getAmount();
        if (gp.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("Insufficient gift point balance");
        }

        // Apply redemption
        gp.setBalance(gp.getBalance().subtract(amount));
        giftPointsRepository.save(gp);

        BigDecimal newTotal = order.getTotalAmount().subtract(amount).max(BigDecimal.ZERO);
        order.setGiftPointsRedeemed(order.getGiftPointsRedeemed().add(amount));
        order.setTotalAmount(newTotal);
        orderRepository.save(order);

        GiftPointsTransaction tx = GiftPointsTransaction.builder()
                .user(user)
                .delta(amount.negate())
                .reason("Redeemed for order #" + order.getId())
                .build();
        txRepository.save(tx);

        return RedeemGiftPointsResponse.builder()
                .updatedTotal(newTotal)
                .pointsRedeemed(amount)
                .remainingBalance(gp.getBalance())
                .build();
    }
}
