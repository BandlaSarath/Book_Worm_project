package com.bookworm.controller;

import com.bookworm.dto.request.PlaceOrderRequest;
import com.bookworm.dto.response.BuyAgainResponse;
import com.bookworm.dto.response.OrderResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.enums.OrderStatus;
import com.bookworm.service.OrderService;
import com.bookworm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController extends BaseController {

    private final OrderService orderService;

    public OrderController(OrderService orderService, UserService userService) {
        super(userService);
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> listOrders(
            Authentication auth,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.listOrders(
                currentUser(auth).getId(), status,
                PageRequest.of(page, size, Sort.by("placedAt").descending())));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(Authentication auth,
                                                     @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(currentUser(auth), request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(Authentication auth, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrder(currentUser(auth).getId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(Authentication auth, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(currentUser(auth).getId(), orderId));
    }

    @PostMapping("/{orderId}/buy-again")
    public ResponseEntity<BuyAgainResponse> buyAgain(Authentication auth, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.buyAgain(currentUser(auth), orderId));
    }
}
