package com.booking.unit;

import com.booking.domain.NotificationRecord;
import com.booking.mapper.NotificationMapper;
import com.booking.mapper.UserMapper;
import com.booking.service.NotificationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchTest {

    @Mock NotificationMapper notificationMapper;
    @Mock UserMapper userMapper;
    @InjectMocks NotificationService notificationService;

    @BeforeAll static void setup() {
        com.booking.util.FieldEncryptor.configure("TestNotifKey1234!");
    }

    @Test void processRetryQueueMarksAsSent() {
        NotificationRecord r = new NotificationRecord(); r.setId(1L);
        when(notificationMapper.findQueued()).thenReturn(List.of(r));
        notificationService.processRetryQueue();
        verify(notificationMapper).updateStatus(1L, "SENT");
    }

    @Test void processRetryQueueHandlesEmpty() {
        when(notificationMapper.findQueued()).thenReturn(List.of());
        notificationService.processRetryQueue();
        verify(notificationMapper, never()).updateStatus(anyLong(), any());
    }

    @Test void retryIncrements() {
        NotificationRecord r = new NotificationRecord(); r.setId(1L); r.setRetryCount(1);
        when(notificationMapper.findById(1L)).thenReturn(r);
        notificationService.retryNotification(1L);
        verify(notificationMapper).incrementRetry(1L);
        verify(notificationMapper).updateStatus(1L, "QUEUED");
    }

    @Test void retryMaxRetriesMarksTerminal() {
        NotificationRecord r = new NotificationRecord(); r.setId(1L); r.setRetryCount(3);
        when(notificationMapper.findById(1L)).thenReturn(r);
        notificationService.retryNotification(1L);
        verify(notificationMapper).markTerminal(1L);
        verify(notificationMapper, never()).incrementRetry(1L);
    }

    @Test void queueHoldNotification() {
        com.booking.domain.User u = new com.booking.domain.User();
        u.setId(4L); u.setEmail("test@t.com");
        when(userMapper.findById(4L)).thenReturn(u);
        notificationService.queueHoldNotification(4L, "ORD-1", "Payment pending");
        verify(notificationMapper).insert(argThat(n -> n.getSubject().contains("HOLD")));
    }

    @Test void queueOverdueNotification() {
        com.booking.domain.User u = new com.booking.domain.User();
        u.setId(4L); u.setEmail("test@t.com");
        when(userMapper.findById(4L)).thenReturn(u);
        notificationService.queueOverdueNotification(4L, "ORD-2");
        verify(notificationMapper).insert(argThat(n -> n.getSubject().contains("OVERDUE")));
    }
}
