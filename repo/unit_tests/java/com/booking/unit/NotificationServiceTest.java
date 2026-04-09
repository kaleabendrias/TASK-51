package com.booking.unit;

import com.booking.domain.*;
import com.booking.mapper.NotificationMapper;
import com.booking.mapper.UserMapper;
import com.booking.service.NotificationService;
import com.booking.util.FieldEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationMapper notificationMapper;
    @Mock com.booking.mapper.NotificationPreferenceMapper prefMapper;
    @Mock UserMapper userMapper;
    @InjectMocks NotificationService notificationService;

    @Test void queueEmailEncryptsRecipient() {
        User u = new User(); u.setId(4L); u.setEmail("test@example.com");
        when(userMapper.findById(4L)).thenReturn(u);
        notificationService.queueEmail(4L, "Subject", "Body", "ORDER", 1L);
        verify(notificationMapper).insert(argThat(r -> {
            assertNotEquals("test@example.com", r.getRecipient());
            assertEquals("EMAIL", r.getChannel());
            assertEquals("QUEUED", r.getStatus());
            return true;
        }));
    }

    @Test void queueSmsEncryptsPhone() {
        User u = new User(); u.setId(4L); u.setPhone("+1-555-0004");
        when(userMapper.findById(4L)).thenReturn(u);
        notificationService.queueSms(4L, "Body", "ORDER", 1L);
        verify(notificationMapper).insert(argThat(r -> {
            assertEquals("SMS", r.getChannel());
            assertNotEquals("+1-555-0004", r.getRecipient());
            return true;
        }));
    }

    @Test void queueEmailNullUserSkips() {
        when(userMapper.findById(99L)).thenReturn(null);
        notificationService.queueEmail(99L, "s", "b", null, null);
        verify(notificationMapper, never()).insert(any());
    }

    @Test void queueSmsNullPhoneSkips() {
        User u = new User(); u.setId(4L); u.setPhone(null);
        when(userMapper.findById(4L)).thenReturn(u);
        notificationService.queueSms(4L, "b", null, null);
        verify(notificationMapper, never()).insert(any());
    }

    @Test void getByUserMasksRecipients() {
        NotificationRecord r = new NotificationRecord();
        r.setChannel("EMAIL");
        r.setRecipient(FieldEncryptor.encrypt("alice@example.com"));
        when(notificationMapper.findByUserId(4L)).thenReturn(List.of(r));
        List<NotificationRecord> result = notificationService.getByUser(4L);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getRecipient().contains("***"));
        assertFalse(result.get(0).getRecipient().contains("alice@example.com"));
    }

    @Test void getByUserMasksSmsRecipients() {
        NotificationRecord r = new NotificationRecord();
        r.setChannel("SMS");
        r.setRecipient(FieldEncryptor.encrypt("+1-555-0004"));
        when(notificationMapper.findByUserId(4L)).thenReturn(List.of(r));
        List<NotificationRecord> result = notificationService.getByUser(4L);
        assertTrue(result.get(0).getRecipient().contains("***"));
    }

    @Test void getByUserHandlesCorruptedEncryption() {
        NotificationRecord r = new NotificationRecord();
        r.setChannel("EMAIL"); r.setRecipient("GARBAGE_DATA!!!");
        when(notificationMapper.findByUserId(4L)).thenReturn(List.of(r));
        List<NotificationRecord> result = notificationService.getByUser(4L);
        assertEquals("***", result.get(0).getRecipient());
    }

    @Test void queueOrderNotificationSendsToBothParties() {
        Order o = new Order(); o.setId(1L); o.setOrderNumber("ORD-1");
        o.setCustomerId(4L); o.setPhotographerId(2L);
        User cu = new User(); cu.setId(4L); cu.setEmail("c@t.com");
        User pu = new User(); pu.setId(2L); pu.setEmail("p@t.com");
        when(userMapper.findById(4L)).thenReturn(cu);
        when(userMapper.findById(2L)).thenReturn(pu);
        notificationService.queueOrderNotification(o, "EVT", "msg");
        verify(notificationMapper, times(2)).insert(any());
    }
}
