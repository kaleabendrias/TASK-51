package com.booking.unit;

import com.booking.controller.NotificationController;
import com.booking.domain.NotificationPreference;
import com.booking.domain.User;
import com.booking.mapper.NotificationPreferenceMapper;
import com.booking.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationController — verifies delegation to NotificationService,
 * the compliance-always-true invariant in preference updates, and the admin guard
 * on the export endpoints.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock NotificationService notificationService;
    @Mock NotificationPreferenceMapper prefMapper;
    @InjectMocks NotificationController controller;

    private MockHttpSession customerSession;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() {
        User customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        customerSession = new MockHttpSession();
        customerSession.setAttribute("currentUser", customer);

        User admin = new User(); admin.setId(1L); admin.setRoleName("ADMINISTRATOR");
        adminSession = new MockHttpSession();
        adminSession.setAttribute("currentUser", admin);
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_delegatesToServiceByUserId() {
        when(notificationService.getByUser(4L)).thenReturn(List.of());

        ResponseEntity<?> resp = controller.list(customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(notificationService).getByUser(4L);
    }

    // ── markRead ─────────────────────────────────────────────────────────────

    @Test
    void markRead_delegatesToServiceAndReturnsMessage() {
        ResponseEntity<?> resp = controller.markRead(7L, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("message"));
        verify(notificationService).markRead(7L, 4L);
    }

    // ── archive ───────────────────────────────────────────────────────────────

    @Test
    void archive_delegatesToServiceAndReturnsMessage() {
        ResponseEntity<?> resp = controller.archive(7L, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("message"));
        verify(notificationService).archive(7L, 4L);
    }

    // ── getPreferences ────────────────────────────────────────────────────────

    @Test
    void getPreferences_existingPref_returnsItDirectly() {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(4L); pref.setOrderUpdates(true); pref.setCompliance(true);
        when(prefMapper.findByUserId(4L)).thenReturn(pref);

        ResponseEntity<?> resp = controller.getPreferences(customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(pref, resp.getBody(), "Controller must return the preference from the mapper");
        verify(prefMapper, never()).insert(any());
    }

    @Test
    void getPreferences_noPref_createsDefaultAndInsertsIt() {
        when(prefMapper.findByUserId(4L)).thenReturn(null);

        ResponseEntity<?> resp = controller.getPreferences(customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(prefMapper).insert(any(NotificationPreference.class));
        NotificationPreference inserted = ((NotificationPreference) resp.getBody());
        assertTrue(inserted.getCompliance(),       "Default compliance must be true");
        assertFalse(inserted.getMuteNonCritical(), "Default muteNonCritical must be false");
    }

    // ── updatePreferences ─────────────────────────────────────────────────────

    @Test
    void updatePreferences_complianceAlwaysTrue() {
        NotificationPreference incoming = new NotificationPreference();
        incoming.setCompliance(false);  // caller tries to disable compliance
        incoming.setOrderUpdates(true);
        when(prefMapper.findByUserId(4L)).thenReturn(new NotificationPreference());
        NotificationPreference saved = new NotificationPreference();
        saved.setCompliance(true);
        when(prefMapper.findByUserId(4L)).thenReturn(saved);

        ResponseEntity<?> resp = controller.updatePreferences(incoming, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(incoming.getCompliance(),
                "Controller must force compliance=true regardless of client input");
    }

    @Test
    void updatePreferences_noExistingPref_insertsNew() {
        NotificationPreference incoming = new NotificationPreference();
        incoming.setOrderUpdates(false);
        when(prefMapper.findByUserId(4L)).thenReturn(null).thenReturn(incoming);

        controller.updatePreferences(incoming, customerSession);

        verify(prefMapper).insert(incoming);
        verify(prefMapper, never()).update(any());
    }

    @Test
    void updatePreferences_existingPref_updatesInPlace() {
        NotificationPreference existing = new NotificationPreference();
        NotificationPreference incoming = new NotificationPreference();
        incoming.setHolds(false);
        when(prefMapper.findByUserId(4L)).thenReturn(existing).thenReturn(incoming);

        controller.updatePreferences(incoming, customerSession);

        verify(prefMapper).update(incoming);
        verify(prefMapper, never()).insert(any());
    }

    // ── export (admin-only) ───────────────────────────────────────────────────

    @Test
    void getReadyForExport_admin_delegatesToServiceAndReturns200() {
        when(notificationService.getReadyForExport()).thenReturn(List.of());

        ResponseEntity<?> resp = controller.getReadyForExport(adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(notificationService).getReadyForExport();
    }

    @Test
    void getReadyForExport_nonAdmin_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> controller.getReadyForExport(customerSession),
                "Non-admin must not access the export endpoint");
    }

    @Test
    void markExported_emptyIds_returns400() {
        ResponseEntity<?> resp = controller.markExported(Map.of("ids", List.of()), adminSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("error"));
        verify(notificationService, never()).markExported(any());
    }

    @Test
    void markExported_nonAdmin_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> controller.markExported(Map.of("ids", List.of(1)), customerSession),
                "Non-admin must not call markExported");
    }
}
