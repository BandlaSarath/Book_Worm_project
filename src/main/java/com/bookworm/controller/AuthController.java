package com.bookworm.controller;

import com.bookworm.dto.request.LoginRequest;
import com.bookworm.dto.request.RegisterRequest;
import com.bookworm.dto.response.AuthResponse;
import com.bookworm.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT is stateless; client discards the token.
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/guest-session")
    public ResponseEntity<AuthResponse> guestSession() {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createGuestSession());
    }
}
