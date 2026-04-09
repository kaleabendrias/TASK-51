package com.booking.unit;

import com.booking.domain.*;
import com.booking.mapper.ListingMapper;
import com.booking.mapper.TimeSlotMapper;
import com.booking.service.TimeSlotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSlotServiceTest {

    @Mock TimeSlotMapper timeSlotMapper;
    @Mock ListingMapper listingMapper;
    @InjectMocks TimeSlotService timeSlotService;

    @Test void reserveSlotSuccess() {
        TimeSlot slot = new TimeSlot(); slot.setId(1L); slot.setCapacity(2); slot.setBookedCount(0); slot.setVersion(0);
        when(timeSlotMapper.findByIdForUpdate(1L)).thenReturn(slot);
        when(timeSlotMapper.incrementBookedCount(1L, 0)).thenReturn(1);
        when(timeSlotMapper.findById(1L)).thenReturn(slot);
        assertNotNull(timeSlotService.reserveSlot(1L));
    }

    @Test void reserveSlotFullThrows() {
        TimeSlot slot = new TimeSlot(); slot.setId(1L); slot.setCapacity(1); slot.setBookedCount(1);
        when(timeSlotMapper.findByIdForUpdate(1L)).thenReturn(slot);
        assertThrows(IllegalStateException.class, () -> timeSlotService.reserveSlot(1L));
    }

    @Test void reserveSlotConcurrentConflict() {
        TimeSlot slot = new TimeSlot(); slot.setId(1L); slot.setCapacity(1); slot.setBookedCount(0); slot.setVersion(0);
        when(timeSlotMapper.findByIdForUpdate(1L)).thenReturn(slot);
        when(timeSlotMapper.incrementBookedCount(1L, 0)).thenReturn(0); // concurrent conflict
        assertThrows(IllegalStateException.class, () -> timeSlotService.reserveSlot(1L));
    }

    @Test void reserveSlotNotFound() {
        when(timeSlotMapper.findByIdForUpdate(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> timeSlotService.reserveSlot(99L));
    }

    @Test void releaseSlotSuccess() {
        TimeSlot slot = new TimeSlot(); slot.setId(1L); slot.setVersion(1);
        when(timeSlotMapper.findByIdForUpdate(1L)).thenReturn(slot);
        timeSlotService.releaseSlot(1L);
        verify(timeSlotMapper).decrementBookedCount(1L, 1);
    }

    @Test void releaseSlotNotFoundNoOp() {
        when(timeSlotMapper.findByIdForUpdate(99L)).thenReturn(null);
        assertDoesNotThrow(() -> timeSlotService.releaseSlot(99L));
    }

    @Test void createSlotByOwner() {
        Listing l = new Listing(); l.setId(1L); l.setPhotographerId(2L); l.setMaxConcurrent(3);
        when(listingMapper.findById(1L)).thenReturn(l);
        TimeSlot s = new TimeSlot(); s.setListingId(1L);
        when(timeSlotMapper.findById(any())).thenReturn(s);
        User owner = new User(); owner.setId(2L); owner.setRoleName("PHOTOGRAPHER");
        timeSlotService.create(s, owner);
        verify(timeSlotMapper).insert(argThat(sl -> sl.getCapacity() == 3));
    }

    @Test void createSlotByNonOwnerDenied() {
        Listing l = new Listing(); l.setId(1L); l.setPhotographerId(2L);
        when(listingMapper.findById(1L)).thenReturn(l);
        TimeSlot s = new TimeSlot(); s.setListingId(1L);
        User other = new User(); other.setId(99L); other.setRoleName("PHOTOGRAPHER");
        assertThrows(SecurityException.class, () -> timeSlotService.create(s, other));
    }
}
