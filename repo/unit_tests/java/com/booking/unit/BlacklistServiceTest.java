package com.booking.unit;

import com.booking.domain.BlacklistEntry;
import com.booking.domain.User;
import com.booking.mapper.BlacklistMapper;
import com.booking.mapper.UserMapper;
import com.booking.service.BlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlacklistServiceTest {

    @Mock BlacklistMapper blacklistMapper;
    @Mock UserMapper userMapper;
    @InjectMocks BlacklistService blacklistService;

    private User admin, customer, adminTarget;

    @BeforeEach void setup() {
        admin = new User(); admin.setId(1L); admin.setRoleName("ADMINISTRATOR");
        customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        adminTarget = new User(); adminTarget.setId(2L); adminTarget.setRoleName("ADMINISTRATOR");
    }

    @Test void blacklistSuccess() {
        when(userMapper.findById(4L)).thenReturn(customer);
        when(blacklistMapper.findActiveByUserId(4L)).thenReturn(null);
        when(blacklistMapper.findById(any())).thenReturn(new BlacklistEntry());
        BlacklistEntry entry = blacklistService.blacklist(4L, "Bad behavior", null, admin);
        verify(blacklistMapper).insert(argThat(e -> e.getDurationDays() == 7)); // default
        verify(userMapper).updateEnabled(4L, false);
    }

    @Test void blacklistCustomDuration() {
        when(userMapper.findById(4L)).thenReturn(customer);
        when(blacklistMapper.findActiveByUserId(4L)).thenReturn(null);
        when(blacklistMapper.findById(any())).thenReturn(new BlacklistEntry());
        blacklistService.blacklist(4L, "Test", 14, admin);
        verify(blacklistMapper).insert(argThat(e -> e.getDurationDays() == 14));
    }

    @Test void blacklistNonAdminDenied() {
        assertThrows(SecurityException.class, () -> blacklistService.blacklist(4L, "x", null, customer));
    }

    @Test void cannotBlacklistAdmin() {
        when(userMapper.findById(2L)).thenReturn(adminTarget);
        assertThrows(IllegalArgumentException.class, () -> blacklistService.blacklist(2L, "x", null, admin));
    }

    @Test void blacklistReplacesExisting() {
        BlacklistEntry existing = new BlacklistEntry(); existing.setId(10L);
        when(userMapper.findById(4L)).thenReturn(customer);
        when(blacklistMapper.findActiveByUserId(4L)).thenReturn(existing);
        when(blacklistMapper.findById(any())).thenReturn(new BlacklistEntry());
        blacklistService.blacklist(4L, "New reason", 5, admin);
        verify(blacklistMapper).deactivate(eq(10L), eq(1L), anyString());
    }

    @Test void liftSuccess() {
        BlacklistEntry entry = new BlacklistEntry(); entry.setId(1L); entry.setActive(true); entry.setUserId(4L);
        when(blacklistMapper.findById(1L)).thenReturn(entry);
        when(blacklistMapper.findById(1L)).thenReturn(entry);
        blacklistService.lift(1L, "Reinstated", admin);
        verify(blacklistMapper).deactivate(1L, 1L, "Reinstated");
        verify(userMapper).updateEnabled(4L, true);
    }

    @Test void liftInactiveFails() {
        BlacklistEntry entry = new BlacklistEntry(); entry.setId(1L); entry.setActive(false);
        when(blacklistMapper.findById(1L)).thenReturn(entry);
        assertThrows(IllegalStateException.class, () -> blacklistService.lift(1L, "x", admin));
    }

    @Test void liftNonAdminDenied() {
        assertThrows(SecurityException.class, () -> blacklistService.lift(1L, "x", customer));
    }

    @Test void blacklistEmptyReasonRejected() {
        assertThrows(IllegalArgumentException.class, () -> blacklistService.blacklist(4L, "", null, admin));
    }

    @Test void blacklistNullReasonRejected() {
        assertThrows(IllegalArgumentException.class, () -> blacklistService.blacklist(4L, null, null, admin));
    }

    @Test void blacklistBlankReasonRejected() {
        assertThrows(IllegalArgumentException.class, () -> blacklistService.blacklist(4L, "   ", null, admin));
    }

    @Test void autoLiftExpired() {
        BlacklistEntry expired = new BlacklistEntry(); expired.setId(1L); expired.setUserId(4L);
        expired.setBlacklistedBy(1L);
        when(blacklistMapper.findExpiredActive(any())).thenReturn(List.of(expired));
        blacklistService.autoLiftExpired();
        verify(blacklistMapper).deactivate(eq(1L), eq(1L), contains("expired"));
        verify(userMapper).updateEnabled(4L, true);
    }

    @Test void isBlacklistedActive() {
        BlacklistEntry entry = new BlacklistEntry(); entry.setActive(true);
        entry.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(blacklistMapper.findActiveByUserId(4L)).thenReturn(entry);
        assertTrue(blacklistService.isBlacklisted(4L));
    }

    @Test void isBlacklistedExpired() {
        BlacklistEntry entry = new BlacklistEntry(); entry.setActive(true);
        entry.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(blacklistMapper.findActiveByUserId(4L)).thenReturn(entry);
        assertFalse(blacklistService.isBlacklisted(4L));
    }

    @Test void isBlacklistedNone() {
        when(blacklistMapper.findActiveByUserId(4L)).thenReturn(null);
        assertFalse(blacklistService.isBlacklisted(4L));
    }
}
