package com.booking.unit;

import com.booking.domain.Listing;
import com.booking.domain.User;
import com.booking.mapper.ListingMapper;
import com.booking.service.ListingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock ListingMapper listingMapper;
    @InjectMocks ListingService listingService;

    User photographer() { User u = new User(); u.setId(2L); u.setRoleName("PHOTOGRAPHER"); return u; }
    User customer() { User u = new User(); u.setId(4L); u.setRoleName("CUSTOMER"); return u; }
    User admin() { User u = new User(); u.setId(1L); u.setRoleName("ADMINISTRATOR"); return u; }

    Listing validListing() {
        Listing l = new Listing(); l.setTitle("Test"); l.setPrice(BigDecimal.valueOf(100));
        l.setDurationMinutes(60); l.setPhotographerId(2L); return l;
    }

    @Test void createByPhotographerSuccess() {
        Listing l = validListing();
        when(listingMapper.findById(any())).thenReturn(l);
        listingService.create(l, photographer());
        verify(listingMapper).insert(argThat(li -> li.getActive() && li.getMaxConcurrent() == 1));
    }

    @Test void createByCustomerDenied() {
        assertThrows(SecurityException.class, () -> listingService.create(validListing(), customer()));
    }

    @Test void createForOtherPhotographerDenied() {
        Listing l = validListing(); l.setPhotographerId(99L);
        assertThrows(SecurityException.class, () -> listingService.create(l, photographer()));
    }

    @Test void createByAdminForAnyPhotographer() {
        Listing l = validListing(); l.setPhotographerId(99L);
        when(listingMapper.findById(any())).thenReturn(l);
        assertDoesNotThrow(() -> listingService.create(l, admin()));
    }

    @Test void createMissingTitleFails() {
        Listing l = validListing(); l.setTitle(null);
        assertThrows(IllegalArgumentException.class, () -> listingService.create(l, photographer()));
    }

    @Test void createZeroPriceFails() {
        Listing l = validListing(); l.setPrice(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> listingService.create(l, photographer()));
    }

    @Test void updateOwnerSuccess() {
        Listing existing = validListing(); existing.setId(1L);
        when(listingMapper.findById(1L)).thenReturn(existing);
        Listing upd = validListing(); upd.setId(1L); upd.setTitle("Updated");
        when(listingMapper.findById(1L)).thenReturn(upd);
        listingService.update(upd, photographer());
        verify(listingMapper).update(any());
    }

    @Test void updateNonOwnerDenied() {
        Listing existing = validListing(); existing.setId(1L);
        when(listingMapper.findById(1L)).thenReturn(existing);
        User other = new User(); other.setId(99L); other.setRoleName("PHOTOGRAPHER");
        Listing upd = validListing(); upd.setId(1L);
        assertThrows(SecurityException.class, () -> listingService.update(upd, other));
    }

    @Test void isOwnerTrue() {
        Listing l = new Listing(); l.setPhotographerId(2L);
        when(listingMapper.findById(1L)).thenReturn(l);
        assertTrue(listingService.isOwner(1L, 2L));
    }

    @Test void isOwnerFalse() {
        Listing l = new Listing(); l.setPhotographerId(2L);
        when(listingMapper.findById(1L)).thenReturn(l);
        assertFalse(listingService.isOwner(1L, 99L));
    }

    @Test void searchDelegatesWithPaginationAndSort() {
        when(listingMapper.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(listingMapper.searchCount(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0L);
        var result = listingService.search("kw", "cat", null, null, null, null, null, null, null, null, null, null, "price_asc", 1, 20);
        assertEquals(0, ((List<?>) result.get("items")).size());
        assertEquals(0L, result.get("total"));
    }
}
