package com.bookworm.controller;

import com.bookworm.config.SecurityConfig;
import com.bookworm.dto.response.AddressResponse;
import com.bookworm.entity.User;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.security.JwtAuthenticationFilter;
import com.bookworm.security.JwtTokenProvider;
import com.bookworm.security.UserDetailsServiceImpl;
import com.bookworm.service.AddressService;
import com.bookworm.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AddressController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("AddressController slice tests")
class AddressControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AddressService addressService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    private static final String USER_EMAIL = "alice@example.com";
    private static final Long   USER_ID    = 1L;

    private User mockUser;
    private AddressResponse addressResp;

    /** A valid request body – all required fields present and postal code in correct format */
    private Map<String, Object> validBody() {
        return Map.of(
                "line1",       "123 Main St",
                "city",        "Springfield",
                "state",       "IL",
                "postalCode",  "62701",
                "country",     "US"
        );
    }

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(USER_ID).email(USER_EMAIL).role(UserRole.CUSTOMER).build();
        addressResp = AddressResponse.builder()
                .id(10L).line1("123 Main St").city("Springfield")
                .state("IL").postalCode("62701").country("US")
                .isDefault(false).build();
    }

    // ── GET /addresses ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /addresses – returns list of addresses for authenticated user (200)")
    void listAddresses_authenticated_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(addressService.listAddresses(USER_ID)).thenReturn(List.of(addressResp));

        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].city").value("Springfield"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("GET /addresses – returns empty list when user has no addresses (200)")
    void listAddresses_empty_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(addressService.listAddresses(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /addresses – unauthenticated is rejected (4xx)")
    void listAddresses_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().is4xxClientError());
    }

    // ── POST /addresses ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /addresses – valid request creates address and returns 201")
    void addAddress_validRequest_returns201() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(addressService.addAddress(eq(mockUser), any())).thenReturn(addressResp);

        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.line1").value("123 Main St"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /addresses – missing line1 returns 400")
    void addAddress_missingLine1_returns400() throws Exception {
        var body = Map.of("city", "Springfield", "state", "IL",
                "postalCode", "62701", "country", "US");

        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /addresses – missing city returns 400")
    void addAddress_missingCity_returns400() throws Exception {
        var body = Map.of("line1", "123 Main St", "state", "IL",
                "postalCode", "62701", "country", "US");

        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("POST /addresses – invalid postal code format returns 400")
    void addAddress_invalidPostalCode_returns400() throws Exception {
        var body = Map.of("line1", "123 Main St", "city", "Springfield",
                "state", "IL", "postalCode", "ABC", "country", "US");

        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /addresses – unauthenticated is rejected (4xx)")
    void addAddress_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().is4xxClientError());
    }

    // ── PUT /addresses/{addressId} ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("PUT /addresses/{id} – valid request updates address and returns 200")
    void updateAddress_validRequest_returns200() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(addressService.updateAddress(eq(USER_ID), eq(10L), any())).thenReturn(addressResp);

        mockMvc.perform(put("/api/v1/addresses/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("PUT /addresses/{id} – address not found (or another user's) returns 404")
    void updateAddress_notFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(addressService.updateAddress(eq(USER_ID), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Address not found: 99"));

        mockMvc.perform(put("/api/v1/addresses/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("PUT /addresses/{id} – missing required fields returns 400")
    void updateAddress_invalidBody_returns400() throws Exception {
        var body = Map.of("line1", "123 Main St"); // missing city, state, postalCode, country

        mockMvc.perform(put("/api/v1/addresses/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /addresses/{addressId} ──────────────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("DELETE /addresses/{id} – deletes owned address and returns 204")
    void deleteAddress_owned_returns204() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        doNothing().when(addressService).deleteAddress(USER_ID, 10L);

        mockMvc.perform(delete("/api/v1/addresses/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("DELETE /addresses/{id} – address not found returns 404")
    void deleteAddress_notFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        doThrow(new ResourceNotFoundException("Address not found: 99"))
                .when(addressService).deleteAddress(USER_ID, 99L);

        mockMvc.perform(delete("/api/v1/addresses/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /addresses/{id} – unauthenticated is rejected (4xx)")
    void deleteAddress_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(delete("/api/v1/addresses/10"))
                .andExpect(status().is4xxClientError());
    }

    // ── PATCH /addresses/{addressId}/default ───────────────────────────────────

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("PATCH /addresses/{id}/default – marks address as default and returns 200")
    void setDefault_owned_returns200() throws Exception {
        AddressResponse defaultResp = AddressResponse.builder()
                .id(10L).line1("123 Main St").city("Springfield")
                .state("IL").postalCode("62701").country("US")
                .isDefault(true).build();

        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(addressService.setDefault(USER_ID, 10L)).thenReturn(defaultResp);

        mockMvc.perform(patch("/api/v1/addresses/10/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    @WithMockUser(username = USER_EMAIL, roles = "CUSTOMER")
    @DisplayName("PATCH /addresses/{id}/default – address not found returns 404")
    void setDefault_notFound_returns404() throws Exception {
        when(userService.findByEmail(USER_EMAIL)).thenReturn(mockUser);
        when(addressService.setDefault(USER_ID, 99L))
                .thenThrow(new ResourceNotFoundException("Address not found: 99"));

        mockMvc.perform(patch("/api/v1/addresses/99/default"))
                .andExpect(status().isNotFound());
    }
}
