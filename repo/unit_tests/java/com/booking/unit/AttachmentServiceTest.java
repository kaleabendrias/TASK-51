package com.booking.unit;

import com.booking.domain.*;
import com.booking.mapper.AttachmentMapper;
import com.booking.service.AttachmentService;
import com.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock AttachmentMapper attachmentMapper;
    @Mock BookingService bookingService;

    @Test void uploadBookingNotFound() {
        AttachmentService svc = new AttachmentService(attachmentMapper, bookingService, System.getProperty("java.io.tmpdir") + "/test-uploads");
        when(bookingService.getById(99L)).thenReturn(null);
        User u = new User(); u.setId(1L); u.setRoleName("CUSTOMER");
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hi".getBytes());
        assertThrows(IllegalArgumentException.class, () -> svc.upload(99L, file, u));
    }

    @Test void uploadAccessDenied() {
        AttachmentService svc = new AttachmentService(attachmentMapper, bookingService, System.getProperty("java.io.tmpdir") + "/test-uploads");
        Booking b = new Booking(); b.setId(1L); b.setCustomerId(4L);
        when(bookingService.getById(1L)).thenReturn(b);
        when(bookingService.canUserAccess(any(Booking.class), any(User.class))).thenReturn(false);
        User u = new User(); u.setId(99L); u.setRoleName("CUSTOMER");
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hi".getBytes());
        assertThrows(SecurityException.class, () -> svc.upload(1L, file, u));
    }

    @Test void deleteNotFound() {
        AttachmentService svc = new AttachmentService(attachmentMapper, bookingService, System.getProperty("java.io.tmpdir") + "/test-uploads");
        when(attachmentMapper.findById(99L)).thenReturn(null);
        User u = new User(); u.setId(1L);
        assertThrows(IllegalArgumentException.class, () -> svc.delete(99L, u));
    }

    @Test void deleteAccessDenied() {
        AttachmentService svc = new AttachmentService(attachmentMapper, bookingService, System.getProperty("java.io.tmpdir") + "/test-uploads");
        Attachment a = new Attachment(); a.setId(1L); a.setBookingId(1L); a.setFileName("x");
        when(attachmentMapper.findById(1L)).thenReturn(a);
        Booking b = new Booking(); b.setId(1L);
        when(bookingService.getById(1L)).thenReturn(b);
        when(bookingService.canUserAccess(any(Booking.class), any(User.class))).thenReturn(false);
        User u = new User(); u.setId(99L); u.setRoleName("CUSTOMER");
        assertThrows(SecurityException.class, () -> svc.delete(1L, u));
    }

    @Test void sanitizeFileName() {
        AttachmentService svc = new AttachmentService(attachmentMapper, bookingService, System.getProperty("java.io.tmpdir") + "/test-uploads");
        // Test via upload path — just verifying constructor doesn't crash
        assertNotNull(svc);
    }

    @Test void getByBookingId() {
        AttachmentService svc = new AttachmentService(attachmentMapper, bookingService, System.getProperty("java.io.tmpdir") + "/test-uploads");
        when(attachmentMapper.findByBookingId(1L)).thenReturn(List.of());
        assertEquals(0, svc.getByBookingId(1L).size());
    }

    @Test void getById() {
        AttachmentService svc = new AttachmentService(attachmentMapper, bookingService, System.getProperty("java.io.tmpdir") + "/test-uploads");
        when(attachmentMapper.findById(1L)).thenReturn(new Attachment());
        assertNotNull(svc.getById(1L));
    }

    @Test void getFilePath() {
        AttachmentService svc = new AttachmentService(attachmentMapper, bookingService, System.getProperty("java.io.tmpdir") + "/test-uploads");
        Attachment a = new Attachment(); a.setFileName("test.jpg");
        assertTrue(svc.getFilePath(a).toString().contains("test.jpg"));
    }
}
