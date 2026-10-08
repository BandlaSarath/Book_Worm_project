package com.bookworm.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AddressRequest {

    @NotBlank
    @Size(max = 200)
    private String line1;

    @Size(max = 200)
    private String line2;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 100)
    private String state;

    @NotBlank
    @Pattern(regexp = "^\\d{4,10}$", message = "Postal code must be 4-10 digits")
    private String postalCode;

    @NotBlank
    @Size(min = 2, max = 60)
    private String country;

    private boolean isDefault = false;
}
