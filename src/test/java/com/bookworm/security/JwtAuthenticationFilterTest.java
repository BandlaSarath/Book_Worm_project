package com.bookworm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter unit tests")
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserDetailsServiceImpl userDetailsService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthenticationFilter filter;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String USER_EMAIL  = "user@example.com";

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContextAfter() {
        SecurityContextHolder.clearContext();
    }

    // ── valid token ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid token – Authentication is set in SecurityContext")
    void validToken_setsAuthentication() throws Exception {
        UserDetails userDetails = new User(USER_EMAIL, "hash",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtTokenProvider.isTokenValid(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractSubject(VALID_TOKEN)).thenReturn(USER_EMAIL);
        when(userDetailsService.loadUserByUsername(USER_EMAIL)).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(USER_EMAIL);
        verify(filterChain).doFilter(request, response);
    }

    // ── invalid token ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("invalid token – no Authentication set, filter chain continues")
    void invalidToken_noAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
        when(jwtTokenProvider.isTokenValid("bad.token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    // ── no Authorization header ────────────────────────────────────────────────

    @Test
    @DisplayName("no Authorization header – no Authentication set, filter chain continues")
    void noHeader_noAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider, never()).isTokenValid(any());
    }

    // ── guest token (user not found) ───────────────────────────────────────────

    @Test
    @DisplayName("guest token – UsernameNotFoundException is swallowed, no Authentication set")
    void guestToken_userNotFound_noAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer guest.token");
        when(jwtTokenProvider.isTokenValid("guest.token")).thenReturn(true);
        when(jwtTokenProvider.extractSubject("guest.token")).thenReturn("guest-some-uuid");
        when(userDetailsService.loadUserByUsername("guest-some-uuid"))
                .thenThrow(new UsernameNotFoundException("not found"));

        filter.doFilterInternal(request, response, filterChain);

        // Exception is caught; context remains empty; chain still proceeds
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
