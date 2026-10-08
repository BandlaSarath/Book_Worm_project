package com.bookworm.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressResponse {
    private Long id;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    // Primitive boolean field named "isDefault": Lombok generates isDefault() getter,
    // which Jackson would serialize as "default" (stripping the "is" prefix).
    // @JsonProperty forces the correct JSON key "isDefault".
    @JsonProperty("isDefault")
    private boolean isDefault;
}
