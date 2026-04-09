package com.booking.unit;

import com.booking.domain.*;
import com.booking.mapper.BookingMapper;
import com.booking.mapper.ServiceMapper;
import com.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingMapper bookingMapper;
    @Mock ServiceMapper serviceMapper;
    @InjectMocks BookingService bookingService;

    private User customer, photographer, admin;
    private Booking makeBooking() {
        Booking b = new Booking(); b.setId(1L); b.setCustomerId(4L); b.setPhotographerId(2L);
        b.setServiceId(1L); b.setBookingDate(LocalDate.now().plusDays(7));
        b.setStartTime(LocalTime.of(9,0)); b.setEndTime(LocalTime.of(10,0));
        b.setStatus("PENDING"); return b;
    }

    @BeforeEach void setup() {
        customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        photographer = new User(); photographer.setId(2L); photographer.setRoleName("PHOTOGRAPHER");
        admin = new User(); admin.setId(1L); admin.setRoleName("ADMINISTRATOR");
    }

    @Test void createSuccess() {
        Booking b = makeBooking();
        Service svc = new Service(); svc.setId(1L); svc.setActive(true); svc.setPrice(BigDecimal.valueOf(100));
        when(serviceMapper.findById(1L)).thenReturn(svc);
        when(bookingMapper.findConflicting(any(),any(),any(),any(),any())).thenReturn(Collections.emptyList());
        when(bookingMapper.findById(any())).thenReturn(b);
        Booking result = bookingService.create(b);
        verify(bookingMapper).insert(any());
        assertEquals("PENDING", result.getStatus());
    }

    @Test void createPastDateFails() {
        Booking b = makeBooking(); b.setBookingDate(LocalDate.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> bookingService.create(b));
    }

    @Test void createEndBeforeStartFails() {
        Booking b = makeBooking(); b.setEndTime(LocalTime.of(8,0));
        assertThrows(IllegalArgumentException.class, () -> bookingService.create(b));
    }

    @Test void createInactiveServiceFails() {
        Booking b = makeBooking();
        Service svc = new Service(); svc.setId(1L); svc.setActive(false);
        when(serviceMapper.findById(1L)).thenReturn(svc);
        assertThrows(IllegalArgumentException.class, () -> bookingService.create(b));
    }

    @Test void createConflictFails() {
        Booking b = makeBooking();
        when(bookingMapper.findConflicting(any(),any(),any(),any(),any())).thenReturn(List.of(new Booking()));
        assertThrows(IllegalStateException.class, () -> bookingService.create(b));
    }

    @Test void createNullDateFails() {
        Booking b = makeBooking(); b.setBookingDate(null);
        assertThrows(IllegalArgumentException.class, () -> bookingService.create(b));
    }

    @Test void createNullTimesFails() {
        Booking b = makeBooking(); b.setStartTime(null);
        assertThrows(IllegalArgumentException.class, () -> bookingService.create(b));
    }

    @Test void createNoPhotographerSkipsConflictCheck() {
        Booking b = makeBooking(); b.setPhotographerId(null);
        Service svc = new Service(); svc.setId(1L); svc.setActive(true); svc.setPrice(BigDecimal.TEN);
        when(serviceMapper.findById(1L)).thenReturn(svc);
        when(bookingMapper.findById(any())).thenReturn(b);
        bookingService.create(b);
        verify(bookingMapper, never()).findConflicting(any(),any(),any(),any(),any());
    }

    @Test void updateSuccess() {
        Booking existing = makeBooking();
        when(bookingMapper.findById(1L)).thenReturn(existing);
        Booking upd = makeBooking();
        when(bookingMapper.findConflicting(any(),any(),any(),any(),any())).thenReturn(Collections.emptyList());
        when(bookingMapper.findById(1L)).thenReturn(upd);
        bookingService.update(upd, customer);
        verify(bookingMapper).update(any());
    }

    @Test void updateNotFoundFails() {
        when(bookingMapper.findById(99L)).thenReturn(null);
        Booking b = new Booking(); b.setId(99L);
        assertThrows(IllegalArgumentException.class, () -> bookingService.update(b, customer));
    }

    @Test void updateDeniedForOtherCustomer() {
        Booking existing = makeBooking(); existing.setCustomerId(99L); existing.setPhotographerId(null);
        when(bookingMapper.findById(1L)).thenReturn(existing);
        assertThrows(SecurityException.class, () -> bookingService.update(makeBooking(), customer));
    }

    @Test void updateStatusValid() {
        Booking existing = makeBooking(); existing.setStatus("PENDING");
        when(bookingMapper.findById(1L)).thenReturn(existing);
        bookingService.updateStatus(1L, "CONFIRMED", admin);
        verify(bookingMapper).updateStatus(1L, "CONFIRMED");
    }

    @Test void updateStatusInvalid() {
        Booking existing = makeBooking(); existing.setStatus("COMPLETED");
        when(bookingMapper.findById(1L)).thenReturn(existing);
        assertThrows(IllegalStateException.class, () -> bookingService.updateStatus(1L, "PENDING", admin));
    }

    @Test void canUserAccessRoles() {
        Booking b = makeBooking();
        assertTrue(bookingService.canUserAccess(b, admin));
        assertTrue(bookingService.canUserAccess(b, customer));
        assertTrue(bookingService.canUserAccess(b, photographer));
        User other = new User(); other.setId(99L); other.setRoleName("CUSTOMER");
        assertFalse(bookingService.canUserAccess(b, other));
    }

    @Test void getForUserRoutes() {
        when(bookingMapper.findByCustomerId(4L)).thenReturn(List.of());
        when(bookingMapper.findByPhotographerId(2L)).thenReturn(List.of());
        when(bookingMapper.findAll()).thenReturn(List.of());
        bookingService.getForUser(customer); verify(bookingMapper).findByCustomerId(4L);
        bookingService.getForUser(photographer); verify(bookingMapper).findByPhotographerId(2L);
        bookingService.getForUser(admin); verify(bookingMapper).findAll();
    }
}
