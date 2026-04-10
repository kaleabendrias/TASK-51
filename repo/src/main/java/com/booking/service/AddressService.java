package com.booking.service;

import com.booking.domain.Address;
import com.booking.domain.User;
import com.booking.mapper.AddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AddressService {

    private static final Pattern ZIP_PATTERN = Pattern.compile("^\\d{5}(-\\d{4})?$");
    private static final Set<String> US_STATES = Set.of(
            "AL","AK","AZ","AR","CA","CO","CT","DE","FL","GA","HI","ID","IL","IN","IA",
            "KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH","NJ",
            "NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT","VT",
            "VA","WA","WV","WI","WY","DC");

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
        validateAddress(address);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            // Lock existing defaults via SELECT FOR UPDATE to prevent concurrent duplicates
            addressMapper.lockByUserId(actor.getId());
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
        address.setUserId(existing.getUserId());
        validateAddress(address);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            // Lock existing defaults via SELECT FOR UPDATE to prevent concurrent duplicates
            addressMapper.lockByUserId(existing.getUserId());
            addressMapper.clearDefault(existing.getUserId());
        }
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

    // ZIP prefix -> state region mapping for consistency checks
    private static final Map<Character, Set<String>> ZIP_STATE_REGIONS = Map.of(
        '0', Set.of("CT","MA","ME","NH","NJ","NY","PR","RI","VT","VI","DC"),
        '1', Set.of("DE","NY","PA"),
        '2', Set.of("DC","MD","NC","SC","VA","WV"),
        '3', Set.of("AL","FL","GA","MS","TN"),
        '4', Set.of("IN","KY","MI","OH"),
        '5', Set.of("IA","MN","MT","ND","NE","SD","WI"),
        '6', Set.of("IL","KS","MO","NE"),
        '7', Set.of("AR","LA","OK","TX"),
        '8', Set.of("AZ","CO","ID","NM","NV","UT","WY"),
        '9', Set.of("AK","CA","HI","OR","WA")
    );

    private void validateAddress(Address address) {
        if ("US".equals(address.getCountry())) {
            if (address.getState() != null) {
                String st = address.getState().toUpperCase();
                if (!US_STATES.contains(st)) {
                    throw new IllegalArgumentException("Invalid US state code: " + address.getState());
                }
                address.setState(st);
            }
            if (address.getPostalCode() != null) {
                if (!ZIP_PATTERN.matcher(address.getPostalCode()).matches()) {
                    throw new IllegalArgumentException("Invalid US ZIP code format. Expected 12345 or 12345-6789");
                }
                // Cross-validate ZIP prefix vs state
                if (address.getState() != null) {
                    char prefix = address.getPostalCode().charAt(0);
                    Set<String> validStates = ZIP_STATE_REGIONS.get(prefix);
                    if (validStates != null && !validStates.contains(address.getState())) {
                        throw new IllegalArgumentException(
                                "ZIP code " + address.getPostalCode() + " is not consistent with state " + address.getState());
                    }
                }
            }
        }
    }
}
