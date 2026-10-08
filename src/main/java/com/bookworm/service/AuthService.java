package com.bookworm.service;

import com.bookworm.dto.request.LoginRequest;
import com.bookworm.dto.request.RegisterRequest;
import com.bookworm.dto.response.AuthResponse;
import com.bookworm.entity.GiftPoints;
import com.bookworm.entity.User;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.BusinessException;
import com.bookworm.exception.DuplicateResourceException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.GiftPointsRepository;
import com.bookworm.repository.UserRepository;
import com.bookworm.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final GiftPointsRepository giftPointsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final BookWormMapper mapper;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${app.jwt.guest-expiration-ms}")
    private long guestExpirationMs;

    public AuthService(UserRepository userRepository,
                       GiftPointsRepository giftPointsRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuthenticationManager authenticationManager,
                       BookWormMapper mapper) {
        this.userRepository = userRepository;
        this.giftPointsRepository = giftPointsRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.mapper = mapper;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(UserRole.CUSTOMER)
                .build();
        user = userRepository.save(user);

        // Initialise gift point wallet
        giftPointsRepository.save(GiftPoints.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build());

        String token = jwtTokenProvider.generateToken(
                user.getEmail(), Map.of("role", user.getRole()));
        return AuthResponse.builder()
                .token(token)
                .expiresIn(expirationMs / 1000)
                .user(mapper.toUserResponse(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("User not found"));

        String token = jwtTokenProvider.generateToken(
                user.getEmail(), Map.of("role", user.getRole()));
        return AuthResponse.builder()
                .token(token)
                .expiresIn(expirationMs / 1000)
                .user(mapper.toUserResponse(user))
                .build();
    }

    public AuthResponse createGuestSession() {
        String sessionId = "guest-" + UUID.randomUUID();
        String token = jwtTokenProvider.generateGuestToken(sessionId);
        return AuthResponse.builder()
                .token(token)
                .expiresIn(guestExpirationMs / 1000)
                .build();
    }
}
