package com.booking.unit;

import com.booking.controller.BlacklistController;
import com.booking.domain.BlacklistEntry;
import com.booking.domain.User;
import com.booking.service.BlacklistService;
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

@ExtendWith(MockitoExtension.class)
class BlacklistControllerTest {

    @Mock BlacklistService blacklistService;
    @InjectMocks BlacklistController controller;

    private MockHttpSession adminSession;
    private MockHttpSession customerSession;

    @BeforeEach
    void setUp() {
        User admin = new User(); admin.setId(1L); admin.setRoleName("ADMINISTRATOR");
        adminSession = new MockHttpSession();
        adminSession.setAttribute("currentUser", admin);

        User customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        customerSession = new MockHttpSession();
        customerSession.setAttribute("currentUser", customer);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_admin_delegatesToServiceAndReturns200() {
        when(blacklistService.getAll()).thenReturn(List.of());

        ResponseEntity<?> resp = controller.list(adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(blacklistService).getAll();
    }

    @Test
    void list_nonAdmin_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> controller.list(customerSession),
                "Non-admin must not access blacklist");
        verify(blacklistService, never()).getAll();
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_admin_existingEntry_returns200() {
        BlacklistEntry entry = new BlacklistEntry(); entry.setId(3L);
        when(blacklistService.getById(3L)).thenReturn(entry);

        ResponseEntity<?> resp = controller.get(3L, adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(entry, resp.getBody());
    }

    @Test
    void get_admin_notFound_returns404() {
        when(blacklistService.getById(999L)).thenReturn(null);

        ResponseEntity<?> resp = controller.get(999L, adminSession);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void get_nonAdmin_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> controller.get(1L, customerSession));
    }

    // ── blacklist ─────────────────────────────────────────────────────────────

    @Test
    void blacklist_admin_happyPath_returns200() {
        BlacklistEntry saved = new BlacklistEntry(); saved.setId(5L);
        when(blacklistService.blacklist(anyLong(), any(), any(), any())).thenReturn(saved);

        Map<String, Object> body = Map.of("userId", 4, "reason", "Abuse", "durationDays", 7);
        ResponseEntity<?> resp = controller.blacklist(body, adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(saved, resp.getBody());
    }

    @Test
    void blacklist_admin_illegalArgument_returns400() {
        when(blacklistService.blacklist(anyLong(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("User not found"));

        Map<String, Object> body = Map.of("userId", 999, "reason", "Test");
        ResponseEntity<?> resp = controller.blacklist(body, adminSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertNotNull(rb.get("error"));
    }

    @Test
    void blacklist_nonAdmin_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> controller.blacklist(Map.of("userId", 4, "reason", "Test"), customerSession));
    }

    // ── lift ──────────────────────────────────────────────────────────────────

    @Test
    void lift_admin_happyPath_returns200() {
        BlacklistEntry lifted = new BlacklistEntry(); lifted.setId(3L);
        when(blacklistService.lift(anyLong(), any(), any())).thenReturn(lifted);

        ResponseEntity<?> resp = controller.lift(3L, Map.of("reason", "Resolved"), adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(lifted, resp.getBody());
    }

    @Test
    void lift_admin_illegalState_returns400() {
        when(blacklistService.lift(anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("Entry already lifted"));

        ResponseEntity<?> resp = controller.lift(3L, Map.of("reason", "Test"), adminSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertNotNull(rb.get("error"));
    }

    @Test
    void lift_nonAdmin_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> controller.lift(3L, Map.of("reason", "X"), customerSession));
    }
}
