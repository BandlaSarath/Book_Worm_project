package com.bookworm.service;

import com.bookworm.dto.request.PaymentCompleteRequest;
import com.bookworm.dto.request.PaymentInitiateRequest;
import com.bookworm.dto.response.PaymentInitiateResponse;
import com.bookworm.dto.response.PaymentResponse;
import com.bookworm.dto.response.PurchaseConfirmationResponse;
import com.bookworm.entity.Order;
import com.bookworm.entity.Payment;
import com.bookworm.enums.OrderStatus;
import com.bookworm.enums.PaymentStatus;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.OrderRepository;
import com.bookworm.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final BookWormMapper mapper;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          BookWormMapper mapper) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.mapper = mapper;
    }

    @Transactional
    public PaymentInitiateResponse initiatePayment(Long userId, PaymentInitiateRequest request) {
        Order order = orderRepository.findByIdAndUserId(request.getOrderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Payment can only be initiated for PENDING orders");
        }

        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            throw new BusinessException("Payment already exists for this order");
        }

        // IMPORTANT: We do NOT persist card numbers or CVV.
        // Only the last four digits are accepted in the request and are never stored.
        String transactionRef = "TXN-" + UUID.randomUUID().toString().toUpperCase();

        Payment payment = Payment.builder()
                .order(order)
                .method(request.getMethod())
                .status(PaymentStatus.PENDING)
                .transactionRef(transactionRef)
                .amount(order.getTotalAmount())
                .build();

        Payment saved = paymentRepository.save(payment);

        return PaymentInitiateResponse.builder()
                .transactionRef(transactionRef)
                .payment(mapper.toPaymentResponse(saved))
                .build();
    }

    @Transactional
    public PurchaseConfirmationResponse completePayment(Long userId, PaymentCompleteRequest request) {
        Order order = orderRepository.findByIdAndUserId(request.getOrderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        Payment payment = paymentRepository.findByTransactionRef(request.getTransactionRef())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for ref: " + request.getTransactionRef()));

        if (!payment.getOrder().getId().equals(order.getId())) {
            throw new BusinessException("Transaction reference does not match the order");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException("Payment already completed");
        }

        boolean success = "SUCCESS".equalsIgnoreCase(request.getMockResult());

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            order.setStatus(OrderStatus.CONFIRMED);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);
        orderRepository.save(order);

        String msg = success
                ? "Your order has been confirmed. Thank you for shopping with Book Worm!"
                : "Payment failed. Please try again.";

        return PurchaseConfirmationResponse.builder()
                .order(mapper.toOrderResponse(order))
                .payment(mapper.toPaymentResponse(payment))
                .message(msg)
                .build();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long userId, Long orderId) {
        orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
        return mapper.toPaymentResponse(payment);
    }
}
