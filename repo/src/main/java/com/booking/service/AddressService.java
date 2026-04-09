package com.booking.service;

import com.booking.domain.Address;
import com.booking.domain.User;
import com.booking.mapper.AddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressService {

    private final AddressMapper addressMapper;

    public AddressService(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public List<Address> getByUser(Long userId) {
        return addressMapper.findByUserId(userId);
    }

    public Address getById(Long id) {
        return addressMapper.findById(id);
    }

    @Transactional
    public Address create(Address address, User actor) {
        address.setUserId(actor.getId());
        if (address.getCountry() == null) address.setCountry("US");
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            addressMapper.clearDefault(actor.getId());
        }
        addressMapper.insert(address);
        return addressMapper.findById(address.getId());
    }

    @Transactional
    public Address update(Address address, User actor) {
        Address existing = addressMapper.findById(address.getId());
        if (existing == null) throw new IllegalArgumentException("Address not found");
        if (!existing.getUserId().equals(actor.getId()) && !"ADMINISTRATOR".equals(actor.getRoleName())) {
            throw new SecurityException("Access denied");
        }
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            addressMapper.clearDefault(existing.getUserId());
        }
        address.setUserId(existing.getUserId());
        addressMapper.update(address);
        return addressMapper.findById(address.getId());
    }

    public void delete(Long id, User actor) {
        Address existing = addressMapper.findById(id);
        if (existing == null) throw new IllegalArgumentException("Address not found");
        if (!existing.getUserId().equals(actor.getId()) && !"ADMINISTRATOR".equals(actor.getRoleName())) {
            throw new SecurityException("Access denied");
        }
        addressMapper.delete(id);
    }
}
