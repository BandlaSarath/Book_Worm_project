package com.bookworm.security;

import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider unit tests")
class JwtTokenProviderTest {

    // Minimum 32-byte key, Base64-encoded — same pattern as application-test.properties
    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW9ubHktbm90LXByb2Q=";
    private static final long EXPIRATION_MS      = 3_600_000L;   // 1 hour
    private static final long GUEST_EXPIRATION_MS = 1_800_000L;  // 30 min

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS, GUEST_EXPIRATION_MS);
    }

    // ── generateToken ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateToken – produces a non-blank token")
    void generateToken_producesNonBlankToken() {
        String token = provider.generateToken("user@example.com", Map.of("role", "CUSTOMER"));
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("generateToken – subject round-trips via extractSubject")
    void generateToken_subjectRoundTrips() {
        String email = "alice@example.com";
        String token = provider.generateToken(email, Map.of("role", "CUSTOMER"));
        assertThat(provider.extractSubject(token)).isEqualTo(email);
    }

    // ── generateGuestToken ─────────────────────────────────────────────────────

    @Test
    @DisplayName("generateGuestToken – subject round-trips for guest session id")
    void generateGuestToken_subjectRoundTrips() {
        String sessionId = "guest-abc-123";
        String token = provider.generateGuestToken(sessionId);
        assertThat(provider.extractSubject(token)).isEqualTo(sessionId);
    }

    // ── isTokenValid ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid – returns true for a freshly generated token")
    void isTokenValid_freshToken_returnsTrue() {
        String token = provider.generateToken("user@example.com", Map.of());
        assertThat(provider.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid – returns false for a tampered token")
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = provider.generateToken("user@example.com", Map.of());
        // Corrupt the signature portion (last segment)
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";
        assertThat(provider.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid – returns false for a blank string")
    void isTokenValid_blankToken_returnsFalse() {
        assertThat(provider.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid – returns false for a token signed with a different key")
    void isTokenValid_differentKeyToken_returnsFalse() {
        // Build a provider with a different secret
        String otherSecret = Encoders.BASE64.encode(
                "another-secret-key-that-is-long-enough-32b".getBytes());
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherSecret, EXPIRATION_MS, GUEST_EXPIRATION_MS);
        String foreignToken = otherProvider.generateToken("user@example.com", Map.of());

        assertThat(provider.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid – returns false for an expired token")
    void isTokenValid_expiredToken_returnsFalse() {
        // Create provider with -1 ms TTL so token expires immediately
        JwtTokenProvider expiredProvider =
                new JwtTokenProvider(TEST_SECRET, -1L, GUEST_EXPIRATION_MS);
        String token = expiredProvider.generateToken("user@example.com", Map.of());
        assertThat(provider.isTokenValid(token)).isFalse();
    }
}
