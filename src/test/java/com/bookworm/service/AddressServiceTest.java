package com.bookworm.service;

import com.bookworm.dto.request.AddressRequest;
import com.bookworm.dto.response.AddressResponse;
import com.bookworm.entity.Address;
import com.bookworm.entity.User;
import com.bookworm.enums.UserRole;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService unit tests")
class AddressServiceTest {

    @Mock private AddressRepository addressRepository;

    private final BookWormMapper mapper = new BookWormMapper();
    private AddressService addressService;

    private static final Long USER_ID    = 1L;
    private static final Long ADDRESS_ID = 10L;
    private static final Long OTHER_UID  = 99L;

    private User user;
    private Address address;
    private AddressRequest validRequest;

    @BeforeEach
    void setUp() {
        addressService = new AddressService(addressRepository, mapper);

        user = User.builder().id(USER_ID).email("alice@example.com").role(UserRole.CUSTOMER).build();

        address = Address.builder()
                .id(ADDRESS_ID).user(user)
                .line1("123 Main St").city("Springfield")
                .state("IL").postalCode("62701").country("US")
                .isDefault(false).build();

        validRequest = new AddressRequest();
        validRequest.setLine1("123 Main St");
        validRequest.setCity("Springfield");
        validRequest.setState("IL");
        validRequest.setPostalCode("62701");
        validRequest.setCountry("US");
    }

    // ── listAddresses ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listAddresses – returns all addresses for the user")
    void listAddresses_returnsUserAddresses() {
        when(addressRepository.findByUserId(USER_ID)).thenReturn(List.of(address));

        List<AddressResponse> result = addressService.listAddresses(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(ADDRESS_ID);
        assertThat(result.get(0).getCity()).isEqualTo("Springfield");
    }

    @Test
    @DisplayName("listAddresses – returns empty list when user has no addresses")
    void listAddresses_noAddresses_returnsEmptyList() {
        when(addressRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<AddressResponse> result = addressService.listAddresses(USER_ID);

        assertThat(result).isEmpty();
    }

    // ── addAddress ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addAddress – saves and returns the new address")
    void addAddress_validRequest_savesAndReturns() {
        when(addressRepository.save(any(Address.class))).thenReturn(address);

        AddressResponse result = addressService.addAddress(user, validRequest);

        assertThat(result.getId()).isEqualTo(ADDRESS_ID);
        assertThat(result.getLine1()).isEqualTo("123 Main St");
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    @DisplayName("addAddress – clears existing defaults when isDefault is true")
    void addAddress_withDefault_clearsExistingDefaults() {
        Address existingDefault = Address.builder()
                .id(5L).user(user).line1("Old St").city("X").state("Y")
                .postalCode("00000").country("US").isDefault(true).build();

        validRequest.setDefault(true);
        when(addressRepository.findByUserIdAndIsDefaultTrue(USER_ID))
                .thenReturn(List.of(existingDefault));
        when(addressRepository.save(existingDefault)).thenReturn(existingDefault);

        Address savedNew = Address.builder()
                .id(11L).user(user).line1("123 Main St").city("Springfield")
                .state("IL").postalCode("62701").country("US").isDefault(true).build();
        when(addressRepository.save(argThat(a -> a.getId() == null))).thenReturn(savedNew);

        AddressResponse result = addressService.addAddress(user, validRequest);

        assertThat(existingDefault.getIsDefault()).isFalse();
        assertThat(result.isDefault()).isTrue();
    }

    // ── updateAddress ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateAddress – updates fields and returns updated address")
    void updateAddress_validRequest_updatesAddress() {
        AddressRequest updateReq = new AddressRequest();
        updateReq.setLine1("456 New Ave");
        updateReq.setCity("Shelbyville");
        updateReq.setState("IL");
        updateReq.setPostalCode("62702");
        updateReq.setCountry("US");

        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));

        Address updated = Address.builder()
                .id(ADDRESS_ID).user(user)
                .line1("456 New Ave").city("Shelbyville")
                .state("IL").postalCode("62702").country("US")
                .isDefault(false).build();
        when(addressRepository.save(address)).thenReturn(updated);

        AddressResponse result = addressService.updateAddress(USER_ID, ADDRESS_ID, updateReq);

        assertThat(result.getLine1()).isEqualTo("456 New Ave");
        assertThat(result.getCity()).isEqualTo("Shelbyville");
    }

    @Test
    @DisplayName("updateAddress – throws ResourceNotFoundException for unknown address")
    void updateAddress_notFound_throwsException() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(USER_ID, ADDRESS_ID, validRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(ADDRESS_ID));
    }

    @Test
    @DisplayName("updateAddress – throws ResourceNotFoundException for another user's address")
    void updateAddress_otherUsersAddress_throwsException() {
        // findByIdAndUserId with OTHER_UID returns empty — isolates to owner
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, OTHER_UID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(OTHER_UID, ADDRESS_ID, validRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteAddress ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAddress – deletes the address when it belongs to the user")
    void deleteAddress_owned_deletesSuccessfully() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));

        addressService.deleteAddress(USER_ID, ADDRESS_ID);

        verify(addressRepository).delete(address);
    }

    @Test
    @DisplayName("deleteAddress – throws ResourceNotFoundException when address not found for user")
    void deleteAddress_notFound_throwsException() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, ADDRESS_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(ADDRESS_ID));
    }

    @Test
    @DisplayName("deleteAddress – throws ResourceNotFoundException for another user's address")
    void deleteAddress_otherUsersAddress_throwsException() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, OTHER_UID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(OTHER_UID, ADDRESS_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── setDefault ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setDefault – marks address as default and clears others")
    void setDefault_owned_setsDefault() {
        Address other = Address.builder()
                .id(20L).user(user).line1("B").city("X").state("Y")
                .postalCode("00001").country("US").isDefault(true).build();

        when(addressRepository.findByUserIdAndIsDefaultTrue(USER_ID))
                .thenReturn(List.of(other));
        when(addressRepository.save(other)).thenReturn(other);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(address));

        Address saved = Address.builder()
                .id(ADDRESS_ID).user(user).line1("123 Main St").city("Springfield")
                .state("IL").postalCode("62701").country("US").isDefault(true).build();
        when(addressRepository.save(address)).thenReturn(saved);

        AddressResponse result = addressService.setDefault(USER_ID, ADDRESS_ID);

        assertThat(other.getIsDefault()).isFalse();
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("setDefault – throws ResourceNotFoundException for unknown address")
    void setDefault_notFound_throwsException() {
        when(addressRepository.findByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(List.of());
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.setDefault(USER_ID, ADDRESS_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
