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
import com.bookworm.enums.UserRole;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.GiftPointsRepository;
import com.bookworm.repository.GiftPointsTransactionRepository;
import com.bookworm.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GiftPointsService unit tests")
class GiftPointsServiceTest {

    @Mock private GiftPointsRepository giftPointsRepository;
    @Mock private GiftPointsTransactionRepository txRepository;
    @Mock private OrderRepository orderRepository;

    private final BookWormMapper mapper = new BookWormMapper();
    private GiftPointsService giftPointsService;

    private static final Long USER_ID    = 1L;
    private static final Long OTHER_UID  = 99L;
    private static final Long ORDER_ID   = 10L;

    private User user;
    private GiftPoints giftPoints;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        giftPointsService = new GiftPointsService(giftPointsRepository, txRepository,
                orderRepository, mapper);

        user = User.builder().id(USER_ID).email("alice@example.com")
                .role(UserRole.CUSTOMER).build();

        giftPoints = GiftPoints.builder().user(user)
                .balance(new BigDecimal("50.00")).build();

        pendingOrder = Order.builder()
                .id(ORDER_ID).user(user).status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("29.99"))
                .giftPointsRedeemed(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("29.99"))
                .items(new ArrayList<>()).build();
    }

    // ── A: getBalance ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBalance – returns balance for user with gift points account")
    void getBalance_existingAccount_returnsBalance() {
        when(giftPointsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(giftPoints));

        BigDecimal result = giftPointsService.getBalance(USER_ID);

        assertThat(result).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("getBalance – returns zero when no gift points account exists")
    void getBalance_noAccount_returnsZero() {
        when(giftPointsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        BigDecimal result = giftPointsService.getBalance(USER_ID);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getBalance – userId scoping: different users get different balances")
    void getBalance_differentUsers_scopedCorrectly() {
        GiftPoints otherGp = GiftPoints.builder()
                .user(User.builder().id(OTHER_UID).build())
                .balance(new BigDecimal("5.00")).build();

        when(giftPointsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(giftPoints));
        when(giftPointsRepository.findByUserId(OTHER_UID)).thenReturn(Optional.of(otherGp));

        assertThat(giftPointsService.getBalance(USER_ID)).isEqualByComparingTo("50.00");
        assertThat(giftPointsService.getBalance(OTHER_UID)).isEqualByComparingTo("5.00");
    }

    // ── A: getTransactions ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getTransactions – returns paged transactions for user")
    void getTransactions_returnsPagedResults() {
        GiftPointsTransaction tx = GiftPointsTransaction.builder()
                .id(1L).user(user)
                .delta(new BigDecimal("-10.00")).reason("Redeemed for order #5").build();

        Pageable pageable = PageRequest.of(0, 20);
        Page<GiftPointsTransaction> page = new PageImpl<>(List.of(tx));
        when(txRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(page);

        PagedResponse<GiftPointTransactionResponse> result =
                giftPointsService.getTransactions(USER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDelta()).isEqualByComparingTo("-10.00");
    }

    // ── A: redeem – happy path ─────────────────────────────────────────────────

    @Test
    @DisplayName("redeem – deducts balance, updates order total, creates transaction")
    void redeem_validRequest_deductsAndUpdatesOrder() {
        RedeemGiftPointsRequest req = new RedeemGiftPointsRequest();
        req.setOrderId(ORDER_ID);
        req.setAmount(new BigDecimal("10.00"));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(pendingOrder));
        when(giftPointsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(giftPoints));
        when(giftPointsRepository.save(giftPoints)).thenReturn(giftPoints);
        when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);
        when(txRepository.save(any(GiftPointsTransaction.class))).thenReturn(null);

        RedeemGiftPointsResponse result = giftPointsService.redeem(user, req);

        assertThat(result.getPointsRedeemed()).isEqualByComparingTo("10.00");
        assertThat(result.getRemainingBalance()).isEqualByComparingTo("40.00");
        assertThat(result.getUpdatedTotal()).isEqualByComparingTo("19.99");
        // balance deducted
        assertThat(giftPoints.getBalance()).isEqualByComparingTo("40.00");
        // transaction saved
        verify(txRepository).save(any(GiftPointsTransaction.class));
    }

    @Test
    @DisplayName("redeem – order total floors at zero when points exceed total")
    void redeem_pointsExceedTotal_floorZero() {
        RedeemGiftPointsRequest req = new RedeemGiftPointsRequest();
        req.setOrderId(ORDER_ID);
        req.setAmount(new BigDecimal("50.00")); // exceeds order total of 29.99

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(pendingOrder));
        when(giftPointsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(giftPoints));
        when(giftPointsRepository.save(giftPoints)).thenReturn(giftPoints);
        when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);
        when(txRepository.save(any())).thenReturn(null);

        RedeemGiftPointsResponse result = giftPointsService.redeem(user, req);

        assertThat(result.getUpdatedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── A: redeem – business rule rejections ───────────────────────────────────

    @Test
    @DisplayName("redeem – insufficient balance throws BusinessException")
    void redeem_insufficientBalance_throws() {
        RedeemGiftPointsRequest req = new RedeemGiftPointsRequest();
        req.setOrderId(ORDER_ID);
        req.setAmount(new BigDecimal("100.00")); // balance is only 50

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(pendingOrder));
        when(giftPointsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(giftPoints));

        assertThatThrownBy(() -> giftPointsService.redeem(user, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient gift point balance");
    }

    @Test
    @DisplayName("redeem – non-PENDING order throws BusinessException")
    void redeem_nonPendingOrder_throws() {
        pendingOrder.setStatus(OrderStatus.CONFIRMED);
        RedeemGiftPointsRequest req = new RedeemGiftPointsRequest();
        req.setOrderId(ORDER_ID);
        req.setAmount(new BigDecimal("5.00"));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> giftPointsService.redeem(user, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("redeem – another user's order throws ResourceNotFoundException")
    void redeem_otherUsersOrder_throws() {
        RedeemGiftPointsRequest req = new RedeemGiftPointsRequest();
        req.setOrderId(ORDER_ID);
        req.setAmount(new BigDecimal("5.00"));

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> giftPointsService.redeem(user, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
