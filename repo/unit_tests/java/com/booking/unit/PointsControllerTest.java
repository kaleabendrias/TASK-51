package com.booking.unit;

import com.booking.controller.PointsController;
import com.booking.domain.User;
import com.booking.mapper.PointsAdjustmentMapper;
import com.booking.mapper.PointsLedgerMapper;
import com.booking.mapper.PointsRuleMapper;
import com.booking.service.PointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PointsController — verifies controller-level logic such as
 * the mandatory-note validation in adjust(), balance delegation, and the admin
 * guard on adjust().
 *
 * Uses direct controller instantiation + MockHttpSession so no Spring context
 * is required.
 */
@ExtendWith(MockitoExtension.class)
class PointsControllerTest {

    @Mock PointsService pointsService;
    @Mock PointsRuleMapper rulesMapper;
    @Mock PointsAdjustmentMapper adjustmentMapper;
    @Mock PointsLedgerMapper ledgerMapper;

    @InjectMocks PointsController controller;

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

    // ── balance ───────────────────────────────────────────────────────────────

    @Test
    void balance_returnsServiceValueWrappedInMap() {
        when(pointsService.getBalance(4L)).thenReturn(150);

        ResponseEntity<?> resp = controller.balance(customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertEquals(150, ((Number) body.get("balance")).intValue(),
                "balance endpoint must return the value from PointsService");
    }

    @Test
    void balance_zeroIsValid() {
        when(pointsService.getBalance(4L)).thenReturn(0);

        ResponseEntity<?> resp = controller.balance(customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertEquals(0, ((Number) body.get("balance")).intValue());
    }

    // ── adjust — mandatory note validation ────────────────────────────────────

    @Test
    void adjust_emptyReason_returns400WithErrorContainingMandatory() {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", 4);
        body.put("points", 50);
        body.put("reason", "");

        ResponseEntity<?> resp = controller.adjust(body, adminSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(),
                "Adjustment with empty reason must be rejected with 400");
        @SuppressWarnings("unchecked")
        Map<String, ?> respBody = (Map<String, ?>) resp.getBody();
        assertTrue(respBody.get("error").toString().toLowerCase().contains("mandatory"),
                "Error message must mention 'mandatory' when reason is blank");
    }

    @Test
    void adjust_nullReason_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", 4);
        body.put("points", 50);
        body.put("reason", null);

        ResponseEntity<?> resp = controller.adjust(body, adminSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(),
                "Adjustment with null reason must be rejected with 400");
    }

    @Test
    void adjust_whitespaceOnlyReason_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", 4);
        body.put("points", 50);
        body.put("reason", "   ");

        ResponseEntity<?> resp = controller.adjust(body, adminSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(),
                "Adjustment with whitespace-only reason must be rejected with 400");
    }

    @Test
    void adjust_positivePoints_awardsPoinitsAndReturnsBalances() {
        when(pointsService.getBalance(4L)).thenReturn(100).thenReturn(150);
        when(pointsService.awardPoints(anyLong(), anyInt(), any(), any(), any(), any()))
                .thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 4);
        body.put("points", 50);
        body.put("reason", "Test bonus");

        ResponseEntity<?> resp = controller.adjust(body, adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> respBody = (Map<String, ?>) resp.getBody();
        assertEquals(100, ((Number) respBody.get("balanceBefore")).intValue());
        assertEquals(150, ((Number) respBody.get("balanceAfter")).intValue());
        assertEquals("Test bonus", respBody.get("reason"));

        verify(pointsService).awardPoints(eq(4L), eq(50), any(), any(), any(), eq("Test bonus"));
        verify(adjustmentMapper).insert(any());
    }

    @Test
    void adjust_negativePoints_deductsPointsAndReturnsBalances() {
        when(pointsService.getBalance(4L)).thenReturn(200).thenReturn(190);
        when(pointsService.deductPoints(anyLong(), anyInt(), any(), any(), any(), any()))
                .thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", 4);
        body.put("points", -10);
        body.put("reason", "Penalty applied");

        ResponseEntity<?> resp = controller.adjust(body, adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(pointsService).deductPoints(eq(4L), eq(10), any(), any(), any(), eq("Penalty applied"));
    }

    // ── Non-admin cannot adjust ───────────────────────────────────────────────

    @Test
    void adjust_nonAdmin_throwsSecurityException() {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", 4); body.put("points", 50); body.put("reason", "hack");

        // PointsController.adjust() calls RoleGuard.requireAdmin(session) which
        // throws SecurityException when the session user is not an ADMINISTRATOR.
        assertThrows(SecurityException.class,
                () -> controller.adjust(body, customerSession),
                "Non-admin must not be allowed to adjust points");
    }

    // ── history ───────────────────────────────────────────────────────────────

    @Test
    void history_delegatesToServiceAndReturns200() {
        when(pointsService.getHistory(4L)).thenReturn(java.util.List.of());

        ResponseEntity<?> resp = controller.history(customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(pointsService).getHistory(4L);
    }
}
