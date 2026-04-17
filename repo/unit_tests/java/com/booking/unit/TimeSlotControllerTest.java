package com.booking.unit;

import com.booking.controller.TimeSlotController;
import com.booking.domain.TimeSlot;
import com.booking.domain.User;
import com.booking.service.TimeSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSlotControllerTest {

    @Mock TimeSlotService timeSlotService;
    @InjectMocks TimeSlotController controller;

    private MockHttpSession photographerSession;
    private MockHttpSession customerSession;

    @BeforeEach
    void setUp() {
        User photographer = new User(); photographer.setId(2L); photographer.setRoleName("PHOTOGRAPHER");
        photographerSession = new MockHttpSession();
        photographerSession.setAttribute("currentUser", photographer);

        User customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        customerSession = new MockHttpSession();
        customerSession.setAttribute("currentUser", customer);
    }

    // ── byListing ─────────────────────────────────────────────────────────────

    @Test
    void byListing_delegatesToServiceAndReturns200() {
        when(timeSlotService.getByListing(7L)).thenReturn(List.of());

        ResponseEntity<?> resp = controller.byListing(7L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(timeSlotService).getByListing(7L);
    }

    // ── available ─────────────────────────────────────────────────────────────

    @Test
    void available_delegatesToServiceAndReturns200() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end   = LocalDate.of(2025, 1, 31);
        when(timeSlotService.getAvailable(7L, start, end)).thenReturn(List.of());

        ResponseEntity<?> resp = controller.available(7L, start, end);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(timeSlotService).getAvailable(7L, start, end);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_photographerUser_delegatesToService() {
        TimeSlot slot = new TimeSlot(); slot.setListingId(3L);
        TimeSlot saved = new TimeSlot(); saved.setId(11L);
        when(timeSlotService.create(any(), any())).thenReturn(saved);

        ResponseEntity<?> resp = controller.create(slot, photographerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(saved, resp.getBody());
    }

    @Test
    void create_securityException_returns403() {
        when(timeSlotService.create(any(), any()))
                .thenThrow(new SecurityException("Only photographers can create slots"));

        ResponseEntity<?> resp = controller.create(new TimeSlot(), customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("error"), "403 body must contain an error key");
    }

    @Test
    void create_illegalArgument_returns400() {
        when(timeSlotService.create(any(), any()))
                .thenThrow(new IllegalArgumentException("Slot date in the past"));

        ResponseEntity<?> resp = controller.create(new TimeSlot(), photographerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertTrue(body.get("error").toString().contains("Slot date in the past"));
    }
}
