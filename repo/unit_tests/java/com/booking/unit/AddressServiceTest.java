package com.booking.unit;

import com.booking.domain.Address;
import com.booking.domain.User;
import com.booking.mapper.AddressMapper;
import com.booking.service.AddressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock AddressMapper addressMapper;
    @InjectMocks AddressService addressService;

    User user(Long id, String role) { User u = new User(); u.setId(id); u.setRoleName(role); return u; }

    @Test void createSetsUserIdAndCountry() {
        Address a = new Address(); a.setStreet("123"); a.setCity("X"); a.setIsDefault(false);
        when(addressMapper.findById(any())).thenReturn(a);
        addressService.create(a, user(4L, "CUSTOMER"));
        verify(addressMapper).insert(argThat(addr -> addr.getUserId().equals(4L) && "US".equals(addr.getCountry())));
    }

    @Test void createDefaultClearsOthers() {
        Address a = new Address(); a.setStreet("123"); a.setCity("X"); a.setIsDefault(true);
        when(addressMapper.findById(any())).thenReturn(a);
        addressService.create(a, user(4L, "CUSTOMER"));
        verify(addressMapper).clearDefault(4L);
    }

    @Test void updateNonOwnerDenied() {
        Address existing = new Address(); existing.setId(1L); existing.setUserId(4L);
        when(addressMapper.findById(1L)).thenReturn(existing);
        Address upd = new Address(); upd.setId(1L);
        assertThrows(SecurityException.class, () -> addressService.update(upd, user(5L, "CUSTOMER")));
    }

    @Test void updateAdminAllowed() {
        Address existing = new Address(); existing.setId(1L); existing.setUserId(4L);
        when(addressMapper.findById(1L)).thenReturn(existing);
        Address upd = new Address(); upd.setId(1L); upd.setStreet("New"); upd.setIsDefault(false);
        when(addressMapper.findById(1L)).thenReturn(existing);
        assertDoesNotThrow(() -> addressService.update(upd, user(1L, "ADMINISTRATOR")));
    }

    @Test void deleteNonOwnerDenied() {
        Address existing = new Address(); existing.setId(1L); existing.setUserId(4L);
        when(addressMapper.findById(1L)).thenReturn(existing);
        assertThrows(SecurityException.class, () -> addressService.delete(1L, user(5L, "CUSTOMER")));
    }

    @Test void deleteNotFoundFails() {
        when(addressMapper.findById(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> addressService.delete(99L, user(4L, "CUSTOMER")));
    }

    @Test void createInvalidZipRejected() {
        Address a = new Address(); a.setStreet("1 St"); a.setCity("X"); a.setCountry("US");
        a.setPostalCode("ABCDE"); a.setIsDefault(false);
        assertThrows(IllegalArgumentException.class, () -> addressService.create(a, user(4L, "CUSTOMER")));
    }

    @Test void createInvalidStateRejected() {
        Address a = new Address(); a.setStreet("1 St"); a.setCity("X"); a.setCountry("US");
        a.setState("ZZ"); a.setPostalCode("12345"); a.setIsDefault(false);
        assertThrows(IllegalArgumentException.class, () -> addressService.create(a, user(4L, "CUSTOMER")));
    }

    @Test void createValidZipAndState() {
        Address a = new Address(); a.setStreet("1 St"); a.setCity("X"); a.setCountry("US");
        a.setState("ca"); a.setPostalCode("90210"); a.setIsDefault(false);
        when(addressMapper.findById(any())).thenReturn(a);
        addressService.create(a, user(4L, "CUSTOMER"));
        verify(addressMapper).insert(argThat(addr -> "CA".equals(addr.getState())));
    }

    @Test void createZipWithExtendedFormat() {
        Address a = new Address(); a.setStreet("1 St"); a.setCity("X"); a.setCountry("US");
        a.setPostalCode("12345-6789"); a.setState("NY"); a.setIsDefault(false);
        when(addressMapper.findById(any())).thenReturn(a);
        addressService.create(a, user(4L, "CUSTOMER"));
        verify(addressMapper).insert(any());
    }
}
