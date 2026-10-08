package com.bookworm.controller;

import com.bookworm.config.SecurityConfig;
import com.bookworm.security.JwtAuthenticationFilter;
import com.bookworm.security.JwtTokenProvider;
import com.bookworm.security.UserDetailsServiceImpl;
import com.bookworm.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("AuthController integration tests")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Test
    @DisplayName("POST /auth/register – valid payload returns 201")
    void register_validPayload_returns201() throws Exception {
        var req = Map.of(
                "email", "alice@example.com",
                "password", "Password1!",
                "firstName", "Alice",
                "lastName", "Smith"
        );

        com.bookworm.dto.response.AuthResponse fakeResp =
                com.bookworm.dto.response.AuthResponse.builder()
                        .token("fake.jwt").expiresIn(86400).build();
        when(authService.register(any())).thenReturn(fakeResp);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("fake.jwt"));
    }

    @Test
    @DisplayName("POST /auth/register – missing email returns 400")
    void register_missingEmail_returns400() throws Exception {
        var req = Map.of(
                "password", "Password1!",
                "firstName", "Alice",
                "lastName", "Smith"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/login – valid payload returns 200")
    void login_validPayload_returns200() throws Exception {
        var req = Map.of("email", "alice@example.com", "password", "Password1!");

        com.bookworm.dto.response.AuthResponse fakeResp =
                com.bookworm.dto.response.AuthResponse.builder()
                        .token("login.jwt").expiresIn(86400).build();
        when(authService.login(any())).thenReturn(fakeResp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login.jwt"));
    }

    @Test
    @DisplayName("POST /auth/guest-session – returns 201 with token")
    void guestSession_returns201() throws Exception {
        com.bookworm.dto.response.AuthResponse fakeResp =
                com.bookworm.dto.response.AuthResponse.builder()
                        .token("guest.jwt").expiresIn(3600).build();
        when(authService.createGuestSession()).thenReturn(fakeResp);

        mockMvc.perform(post("/api/v1/auth/guest-session"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("guest.jwt"));
    }
}
