package com.bookworm.controller;

import com.bookworm.dto.request.PaymentCompleteRequest;
import com.bookworm.dto.request.PaymentInitiateRequest;
import com.bookworm.dto.response.PaymentInitiateResponse;
import com.bookworm.dto.response.PaymentResponse;
import com.bookworm.dto.response.PurchaseConfirmationResponse;
import com.bookworm.service.PaymentService;
import com.bookworm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController extends BaseController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService, UserService userService) {
        super(userService);
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<PaymentInitiateResponse> initiate(Authentication auth,
                                                             @Valid @RequestBody PaymentInitiateRequest request) {
        return ResponseEntity.ok(paymentService.initiatePayment(currentUser(auth).getId(), request));
    }

    @PostMapping("/complete")
    public ResponseEntity<PurchaseConfirmationResponse> complete(Authentication auth,
                                                                  @Valid @RequestBody PaymentCompleteRequest request) {
        return ResponseEntity.ok(paymentService.completePayment(currentUser(auth).getId(), request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> getPayment(Authentication auth, @PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(currentUser(auth).getId(), orderId));
    }
}
