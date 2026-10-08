package com.bookworm.service;

import com.bookworm.dto.request.PlaceOrderRequest;
import com.bookworm.dto.response.BookResponse;
import com.bookworm.dto.response.BuyAgainResponse;
import com.bookworm.dto.response.CartResponse;
import com.bookworm.dto.response.OrderResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.entity.*;
import com.bookworm.enums.BookAvailability;
import com.bookworm.enums.OrderStatus;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final GiftPointsRepository giftPointsRepository;
    private final GiftPointsTransactionRepository giftPointsTxRepository;
    private final BookWormMapper mapper;
    private final CartService cartService;

    public OrderService(OrderRepository orderRepository,
                        AddressRepository addressRepository,
                        CartRepository cartRepository,
                        GiftPointsRepository giftPointsRepository,
                        GiftPointsTransactionRepository giftPointsTxRepository,
                        BookWormMapper mapper,
                        CartService cartService) {
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.cartRepository = cartRepository;
        this.giftPointsRepository = giftPointsRepository;
        this.giftPointsTxRepository = giftPointsTxRepository;
        this.mapper = mapper;
        this.cartService = cartService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> listOrders(Long userId, OrderStatus status, Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findByUserIdAndStatus(userId, status, pageable)
                : orderRepository.findByUserId(userId, pageable);
        return mapper.toPagedResponse(page.map(mapper::toOrderResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return mapper.toOrderResponse(order);
    }

    @Transactional
    public OrderResponse placeOrder(User user, PlaceOrderRequest request) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("Cart is empty"));

        if (cart.getItems().isEmpty()) {
            throw new BusinessException("Cannot place order with empty cart");
        }

        Address address = addressRepository.findByIdAndUserId(request.getAddressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + request.getAddressId()));

        BigDecimal subtotal = cart.getSubtotal();
        BigDecimal giftPointsToRedeem = request.getGiftPointsToRedeem() != null
                ? request.getGiftPointsToRedeem() : BigDecimal.ZERO;

        if (giftPointsToRedeem.compareTo(BigDecimal.ZERO) > 0) {
            GiftPoints gp = giftPointsRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new BusinessException("Gift points account not found"));
            if (gp.getBalance().compareTo(giftPointsToRedeem) < 0) {
                throw new BusinessException("Insufficient gift point balance");
            }
            gp.setBalance(gp.getBalance().subtract(giftPointsToRedeem));
            giftPointsRepository.save(gp);

            GiftPointsTransaction tx = GiftPointsTransaction.builder()
                    .user(user)
                    .delta(giftPointsToRedeem.negate())
                    .reason("Redeemed for order")
                    .build();
            giftPointsTxRepository.save(tx);
        }

        BigDecimal total = subtotal.subtract(giftPointsToRedeem).max(BigDecimal.ZERO);

        Order order = Order.builder()
                .user(user)
                .deliveryAddress(address)
                .status(OrderStatus.PENDING)
                .subtotal(subtotal)
                .giftPointsRedeemed(giftPointsToRedeem)
                .totalAmount(total)
                .build();

        for (CartItem cartItem : cart.getItems()) {
            OrderItem oi = OrderItem.builder()
                    .order(order)
                    .book(cartItem.getBook())
                    .quantity(cartItem.getQuantity())
                    .priceAtPurchase(cartItem.getPriceSnapshot())
                    .build();
            order.getItems().add(oi);
        }

        Order saved = orderRepository.save(order);

        // Clear the cart
        cart.getItems().clear();
        cartRepository.save(cart);

        return mapper.toOrderResponse(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.isCancellationEligible()) {
            throw new BusinessException(
                    "Order cannot be cancelled: either it is past the 48-hour window, " +
                    "already shipped/delivered, or already cancelled.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        return mapper.toOrderResponse(orderRepository.save(order));
    }

    @Transactional
    public BuyAgainResponse buyAgain(User user, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        Cart cart = cartService.getOrCreateCartForUser(user);
        List<BookResponse> skipped = new ArrayList<>();

        for (OrderItem oi : order.getItems()) {
            Book book = oi.getBook();
            if (book.getAvailability() == BookAvailability.OUT_OF_STOCK || book.getStock() < 1) {
                skipped.add(mapper.toBookResponse(book));
                continue;
            }
            CartItem existing = cart.getItems().stream()
                    .filter(ci -> ci.getBook().getId().equals(book.getId()))
                    .findFirst().orElse(null);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + oi.getQuantity());
            } else {
                cart.getItems().add(CartItem.builder()
                        .cart(cart)
                        .book(book)
                        .quantity(oi.getQuantity())
                        .priceSnapshot(book.getPrice())
                        .build());
            }
        }

        CartResponse cartResponse = mapper.toCartResponse(cartRepository.save(cart));
        return BuyAgainResponse.builder()
                .cart(cartResponse)
                .skippedItems(skipped)
                .build();
    }
}
