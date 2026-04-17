package com.booking.unit;

import com.booking.controller.OrderController;
import com.booking.domain.Order;
import com.booking.domain.User;
import com.booking.service.IdempotencyService;
import com.booking.service.IdempotencyService.IdempotencyResult;
import com.booking.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class OrderControllerTest {

    @Mock OrderService orderService;
    @Mock IdempotencyService idempotencyService;
    @Spy  ObjectMapper objectMapper;
    @InjectMocks OrderController controller;

    private MockHttpSession customerSession;
    private MockHttpSession adminSession;

    private static final IdempotencyResult NOT_DUPLICATE =
            new IdempotencyResult(false, null, null);

    @BeforeEach
    void setUp() {
        User customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        customerSession = new MockHttpSession();
        customerSession.setAttribute("currentUser", customer);

        User admin = new User(); admin.setId(1L); admin.setRoleName("ADMINISTRATOR");
        adminSession = new MockHttpSession();
        adminSession.setAttribute("currentUser", admin);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_delegatesToServiceForCurrentUser() {
        when(orderService.getForUser(any())).thenReturn(List.of());

        ResponseEntity<?> resp = controller.list(customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(orderService).getForUser(argThat(u -> u.getId().equals(4L)));
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_existingOrder_accessAllowed_returns200() {
        Order order = new Order(); order.setId(10L);
        when(orderService.getById(10L)).thenReturn(order);
        when(orderService.canUserAccess(any(), any())).thenReturn(true);

        ResponseEntity<?> resp = controller.get(10L, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(order, resp.getBody());
    }

    @Test
    void get_nonExistentOrder_returns404() {
        when(orderService.getById(999L)).thenReturn(null);

        ResponseEntity<?> resp = controller.get(999L, customerSession);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void get_accessDenied_returns403() {
        Order order = new Order(); order.setId(10L);
        when(orderService.getById(10L)).thenReturn(order);
        when(orderService.canUserAccess(any(), any())).thenReturn(false);

        ResponseEntity<?> resp = controller.get(10L, customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("error"));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_duplicateIdempotencyKey_returnsCachedResponse() {
        IdempotencyResult duplicate = new IdempotencyResult(true, 200, "{\"id\":10}");
        when(idempotencyService.checkToken(anyString(), anyString(), any())).thenReturn(duplicate);

        Map<String, Object> body = Map.of("listingId", 1, "timeSlotId", 2);
        ResponseEntity<?> resp = controller.create(body, "idem-key-123", customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("{\"id\":10}", resp.getBody());
        verify(orderService, never()).createOrder(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_illegalArgument_returns400AndRecordsResponse() {
        when(idempotencyService.checkToken(anyString(), anyString(), any())).thenReturn(NOT_DUPLICATE);
        when(orderService.createOrder(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Slot not available"));

        Map<String, Object> body = Map.of("listingId", 1, "timeSlotId", 2);
        ResponseEntity<?> resp = controller.create(body, "idem-key-xyz", customerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertTrue(rb.get("error").toString().contains("Slot not available"));
    }

    // ── executeAction (via confirm) ───────────────────────────────────────────

    @Test
    void confirm_duplicateIdempotencyKey_returnsCachedResponse() {
        IdempotencyResult duplicate = new IdempotencyResult(true, 200, "{\"status\":\"CONFIRMED\"}");
        when(idempotencyService.checkToken(anyString(), anyString(), anyLong())).thenReturn(duplicate);

        ResponseEntity<?> resp = controller.confirm(10L, "idem-dup", customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(orderService, never()).confirm(any(), any());
    }

    @Test
    void confirm_orderNotFound_returns404() {
        when(idempotencyService.checkToken(anyString(), anyString(), anyLong())).thenReturn(NOT_DUPLICATE);
        when(orderService.getById(10L)).thenReturn(null);

        ResponseEntity<?> resp = controller.confirm(10L, "idem-new", customerSession);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void confirm_accessDenied_returns403() {
        when(idempotencyService.checkToken(anyString(), anyString(), anyLong())).thenReturn(NOT_DUPLICATE);
        Order order = new Order(); order.setId(10L);
        when(orderService.getById(10L)).thenReturn(order);
        when(orderService.canUserAccess(any(), any())).thenReturn(false);

        ResponseEntity<?> resp = controller.confirm(10L, "idem-new", customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void confirm_illegalState_returns400() {
        when(idempotencyService.checkToken(anyString(), anyString(), anyLong())).thenReturn(NOT_DUPLICATE);
        Order order = new Order(); order.setId(10L);
        when(orderService.getById(10L)).thenReturn(order);
        when(orderService.canUserAccess(any(), any())).thenReturn(true);
        when(orderService.confirm(anyLong(), any())).thenThrow(new IllegalStateException("Wrong status"));

        ResponseEntity<?> resp = controller.confirm(10L, "idem-new", customerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertTrue(body.get("error").toString().contains("Wrong status"));
    }

    // ── audit ─────────────────────────────────────────────────────────────────

    @Test
    void audit_existingOrderWithAccess_returns200() {
        Order order = new Order(); order.setId(10L);
        when(orderService.getById(10L)).thenReturn(order);
        when(orderService.canUserAccess(any(), any())).thenReturn(true);
        when(orderService.getAuditTrail(10L)).thenReturn(List.of());

        ResponseEntity<?> resp = controller.audit(10L, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(orderService).getAuditTrail(10L);
    }

    @Test
    void audit_notFound_returns404() {
        when(orderService.getById(999L)).thenReturn(null);

        ResponseEntity<?> resp = controller.audit(999L, customerSession);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}
