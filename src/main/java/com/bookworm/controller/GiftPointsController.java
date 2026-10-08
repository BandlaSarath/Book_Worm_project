package com.bookworm.controller;

import com.bookworm.dto.request.RedeemGiftPointsRequest;
import com.bookworm.dto.response.GiftPointTransactionResponse;
import com.bookworm.dto.response.PagedResponse;
import com.bookworm.dto.response.RedeemGiftPointsResponse;
import com.bookworm.service.GiftPointsService;
import com.bookworm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/gift-points")
public class GiftPointsController extends BaseController {

    private final GiftPointsService giftPointsService;

    public GiftPointsController(GiftPointsService giftPointsService, UserService userService) {
        super(userService);
        this.giftPointsService = giftPointsService;
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, BigDecimal>> getBalance(Authentication auth) {
        BigDecimal balance = giftPointsService.getBalance(currentUser(auth).getId());
        return ResponseEntity.ok(Map.of("balance", balance));
    }

    @GetMapping("/transactions")
    public ResponseEntity<PagedResponse<GiftPointTransactionResponse>> getTransactions(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(giftPointsService.getTransactions(
                currentUser(auth).getId(), PageRequest.of(page, size)));
    }

    @PostMapping("/redeem")
    public ResponseEntity<RedeemGiftPointsResponse> redeem(Authentication auth,
                                                            @Valid @RequestBody RedeemGiftPointsRequest request) {
        return ResponseEntity.ok(giftPointsService.redeem(currentUser(auth), request));
    }
}
