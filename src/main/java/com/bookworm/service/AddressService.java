package com.bookworm.service;

import com.bookworm.dto.request.AddressRequest;
import com.bookworm.dto.response.AddressResponse;
import com.bookworm.entity.Address;
import com.bookworm.entity.User;
import com.bookworm.exception.ResourceNotFoundException;
import com.bookworm.mapper.BookWormMapper;
import com.bookworm.repository.AddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final BookWormMapper mapper;

    public AddressService(AddressRepository addressRepository, BookWormMapper mapper) {
        this.addressRepository = addressRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(Long userId) {
        return addressRepository.findByUserId(userId)
                .stream().map(mapper::toAddressResponse).collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse addAddress(User user, AddressRequest request) {
        if (request.isDefault()) {
            clearExistingDefaults(user.getId());
        }
        Address address = Address.builder()
                .user(user)
                .line1(request.getLine1())
                .line2(request.getLine2())
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .isDefault(request.isDefault())
                .build();
        return mapper.toAddressResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));

        if (request.isDefault()) {
            clearExistingDefaults(userId);
        }
        address.setLine1(request.getLine1());
        address.setLine2(request.getLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        address.setIsDefault(request.isDefault());
        return mapper.toAddressResponse(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));
        addressRepository.delete(address);
    }

    @Transactional
    public AddressResponse setDefault(Long userId, Long addressId) {
        clearExistingDefaults(userId);
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));
        address.setIsDefault(true);
        return mapper.toAddressResponse(addressRepository.save(address));
    }

    private void clearExistingDefaults(Long userId) {
        addressRepository.findByUserIdAndIsDefaultTrue(userId)
                .forEach(a -> {
                    a.setIsDefault(false);
                    addressRepository.save(a);
                });
    }
}
