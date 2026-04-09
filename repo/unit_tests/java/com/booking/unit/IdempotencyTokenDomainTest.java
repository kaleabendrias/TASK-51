package com.booking.unit;

import com.booking.domain.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class IdempotencyTokenDomainTest {

    @Test void tokenExpired() {
        IdempotencyToken t = new IdempotencyToken();
        t.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertTrue(t.isExpired());
    }

    @Test void tokenNotExpired() {
        IdempotencyToken t = new IdempotencyToken();
        t.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        assertFalse(t.isExpired());
    }

    @Test void tokenNullExpiry() {
        IdempotencyToken t = new IdempotencyToken();
        t.setExpiresAt(null);
        assertFalse(t.isExpired());
    }

    @Test void blacklistEntryExpired() {
        BlacklistEntry e = new BlacklistEntry();
        e.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertTrue(e.isExpired());
    }

    @Test void blacklistEntryNotExpired() {
        BlacklistEntry e = new BlacklistEntry();
        e.setExpiresAt(LocalDateTime.now().plusDays(1));
        assertFalse(e.isExpired());
    }

    @Test void timeSlotHasAvailability() {
        TimeSlot s = new TimeSlot();
        s.setCapacity(3); s.setBookedCount(2);
        assertTrue(s.hasAvailability());
        s.setBookedCount(3);
        assertFalse(s.hasAvailability());
    }
}
