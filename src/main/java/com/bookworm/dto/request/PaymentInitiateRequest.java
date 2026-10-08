package com.bookworm.dto.request;

import com.bookworm.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentInitiateRequest {

    @NotNull
    private Long orderId;

    @NotNull
    private PaymentMethod method;

    // Card details – NEVER persisted. Present only for validation in mock.
    private String cardHolderName;
    private String lastFourDigits;   // only last 4 digits accepted; full number must NOT be sent
    private Integer expiryMonth;
    private Integer expiryYear;

    // UPI
    private String upiId;

    // Wallet
    private String walletProvider;
}
