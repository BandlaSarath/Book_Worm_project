package com.bookworm.controller;

import com.bookworm.dto.request.AddressRequest;
import com.bookworm.dto.response.AddressResponse;
import com.bookworm.service.AddressService;
import com.bookworm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController extends BaseController {

    private final AddressService addressService;

    public AddressController(AddressService addressService, UserService userService) {
        super(userService);
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> listAddresses(Authentication auth) {
        return ResponseEntity.ok(addressService.listAddresses(currentUser(auth).getId()));
    }

    @PostMapping
    public ResponseEntity<AddressResponse> addAddress(Authentication auth,
                                                       @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.addAddress(currentUser(auth), request));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(Authentication auth,
                                                          @PathVariable Long addressId,
                                                          @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(currentUser(auth).getId(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(Authentication auth, @PathVariable Long addressId) {
        addressService.deleteAddress(currentUser(auth).getId(), addressId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{addressId}/default")
    public ResponseEntity<AddressResponse> setDefault(Authentication auth, @PathVariable Long addressId) {
        return ResponseEntity.ok(addressService.setDefault(currentUser(auth).getId(), addressId));
    }
}
