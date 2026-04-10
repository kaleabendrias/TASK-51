package com.booking.unit;

import com.booking.domain.NotificationRecord;
import com.booking.mapper.NotificationMapper;
import com.booking.mapper.NotificationPreferenceMapper;
import com.booking.mapper.UserMapper;
import com.booking.service.NotificationDispatcher;
import com.booking.service.NotificationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationExportTest {

    @Mock NotificationMapper notificationMapper;
    @Mock NotificationPreferenceMapper prefMapper;
    @Mock UserMapper userMapper;
    @Mock NotificationDispatcher dispatcher;
    @InjectMocks NotificationService notificationService;

    @BeforeAll static void setup() {
        com.booking.util.FieldEncryptor.configure("TestExportKey1234ForAES256Minimum!!");
    }

    @Test void getReadyForExportDelegates() {
        when(notificationMapper.findByStatus("READY_FOR_EXPORT")).thenReturn(List.of(new NotificationRecord()));
        assertEquals(1, notificationService.getReadyForExport().size());
    }

    @Test void markExportedUpdatesStatus() {
        int count = notificationService.markExported(List.of(1L, 2L, 3L));
        assertEquals(3, count);
        verify(notificationMapper, times(3)).updateStatus(anyLong(), eq("EXPORTED"));
    }

    @Test void markExportedEmptyList() {
        assertEquals(0, notificationService.markExported(List.of()));
    }

    @Test void queueApprovalNotification() {
        com.booking.domain.User u = new com.booking.domain.User();
        u.setId(4L); u.setEmail("test@t.com");
        when(userMapper.findById(4L)).thenReturn(u);
        notificationService.queueApprovalNotification(4L, "ORD-1", "Refund approved");
        verify(notificationMapper).insert(argThat(n -> n.getSubject().contains("APPROVAL")));
    }

    @Test void isMutedChecksApprovalPreference() {
        com.booking.domain.NotificationPreference pref = new com.booking.domain.NotificationPreference();
        pref.setApprovals(false); pref.setOrderUpdates(true); pref.setHolds(true);
        pref.setReminders(true); pref.setMuteNonCritical(false);
        when(prefMapper.findByUserId(4L)).thenReturn(pref);
        com.booking.domain.User u = new com.booking.domain.User();
        u.setId(4L); u.setEmail("test@t.com");
        when(userMapper.findById(4L)).thenReturn(u);
        // Approval notification should be muted
        notificationService.queueApprovalNotification(4L, "ORD-1", "test");
        verify(notificationMapper, never()).insert(any());
    }

    @Test void complianceNeverMuted() {
        com.booking.domain.User u = new com.booking.domain.User();
        u.setId(4L); u.setEmail("test@t.com");
        when(userMapper.findById(4L)).thenReturn(u);
        notificationService.queueEmail(4L, "Compliance", "Body", "COMPLIANCE", null);
        verify(notificationMapper).insert(any()); // Not muted even if prefs say mute
    }
}
