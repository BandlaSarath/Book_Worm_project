package com.bookworm.service;

import com.bookworm.dto.request.LoginRequest;
import com.bookworm.dto.request.RegisterRequest;
import com.bookworm.dto.response.AuthResponse;
import com.bookworm.entity.GiftPoints;
import com.bookworm.entity.User;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.DuplicateResourceException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.GiftPointsRepository;
import com.bookworm.repository.UserRepository;
import com.bookworm.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private GiftPointsRepository giftPointsRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private BookWormMapper mapper;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expirationMs", 86400000L);
        ReflectionTestUtils.setField(authService, "guestExpirationMs", 3600000L);
    }

    @Test
    @DisplayName("register – new email creates user and returns token")
    void register_newEmail_returnsToken() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@example.com");
        req.setPassword("Password1!");
        req.setFirstName("Alice");
        req.setLastName("Smith");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(req.getPassword())).thenReturn("hashed");

        User savedUser = User.builder()
                .id(1L).email(req.getEmail())
                .passwordHash("hashed")
                .firstName("Alice").lastName("Smith")
                .role(UserRole.CUSTOMER)
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(giftPointsRepository.save(any(GiftPoints.class))).thenReturn(
                GiftPoints.builder().user(savedUser).balance(BigDecimal.ZERO).build());
        when(jwtTokenProvider.generateToken(anyString(), anyMap())).thenReturn("mock.jwt.token");
        when(mapper.toUserResponse(savedUser)).thenCallRealMethod();

        // Use real mapper for this call
        BookWormMapper realMapper = new BookWormMapper();
        ReflectionTestUtils.setField(authService, "mapper", realMapper);

        AuthResponse response = authService.register(req);

        assertThat(response.getToken()).isNotBlank();
        verify(userRepository).save(any(User.class));
        verify(giftPointsRepository).save(any(GiftPoints.class));
    }

    @Test
    @DisplayName("register – duplicate email throws DuplicateResourceException")
    void register_duplicateEmail_throwsException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@example.com");
        req.setPassword("Password1!");
        req.setFirstName("Bob");
        req.setLastName("Jones");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("createGuestSession – returns token with non-null expiresIn")
    void createGuestSession_returnsGuestToken() {
        when(jwtTokenProvider.generateGuestToken(anyString())).thenReturn("guest.jwt.token");

        AuthResponse response = authService.createGuestSession();

        assertThat(response.getToken()).isEqualTo("guest.jwt.token");
        assertThat(response.getExpiresIn()).isPositive();
    }
}
