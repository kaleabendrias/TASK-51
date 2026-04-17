package com.booking.unit;

import com.booking.controller.UserController;
import com.booking.domain.User;
import com.booking.service.UserService;
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
 * Unit tests for UserController — verifies the admin guard on list/patch/setEnabled,
 * the ownership check on get, and delegation to UserService.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock UserService userService;
    @InjectMocks UserController controller;

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

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_admin_delegatesToServiceAndReturns200() {
        when(userService.getAll()).thenReturn(List.of());

        ResponseEntity<?> resp = controller.list(adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(userService).getAll();
    }

    @Test
    void list_nonAdmin_returns403WithoutCallingService() {
        ResponseEntity<?> resp = controller.list(customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("error"), "403 body must contain an error key");
        verify(userService, never()).getAll();
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_ownProfile_returns200() {
        User user = new User(); user.setId(4L);
        when(userService.getById(4L)).thenReturn(user);

        ResponseEntity<?> resp = controller.get(4L, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void get_otherUserProfile_returns403WithoutCallingService() {
        ResponseEntity<?> resp = controller.get(99L, customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(userService, never()).getById(99L);
    }

    @Test
    void get_adminCanAccessAnyUser() {
        User target = new User(); target.setId(4L);
        when(userService.getById(4L)).thenReturn(target);

        ResponseEntity<?> resp = controller.get(4L, adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void get_nonExistentUser_returns404() {
        when(userService.getById(999L)).thenReturn(null);

        ResponseEntity<?> resp = controller.get(999L, adminSession);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // ── patchUpdate ───────────────────────────────────────────────────────────

    @Test
    void patchUpdate_admin_callsServiceAndReturnsMessage() {
        Map<String, Object> fields = Map.of("fullName", "Updated Name");

        ResponseEntity<?> resp = controller.patchUpdate(4L, fields, adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("message"), "Response must contain a message on success");
        verify(userService).patchUpdate(4L, fields);
    }

    @Test
    void patchUpdate_nonAdmin_returns403WithoutCallingService() {
        ResponseEntity<?> resp = controller.patchUpdate(4L, Map.of(), customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(userService, never()).patchUpdate(any(), any());
    }

    @Test
    void patchUpdate_illegalArgument_returns400WithServiceMessage() {
        doThrow(new IllegalArgumentException("Invalid field value"))
                .when(userService).patchUpdate(any(), any());

        ResponseEntity<?> resp = controller.patchUpdate(4L, Map.of("roleId", -1), adminSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertTrue(body.get("error").toString().contains("Invalid field value"));
    }

    // ── setEnabled ────────────────────────────────────────────────────────────

    @Test
    void setEnabled_admin_disablesUserAndReturnsMessage() {
        ResponseEntity<?> resp = controller.setEnabled(4L, Map.of("enabled", false), adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("message"));
        verify(userService).setEnabled(4L, false);
    }

    @Test
    void setEnabled_admin_enablesUser() {
        ResponseEntity<?> resp = controller.setEnabled(4L, Map.of("enabled", true), adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(userService).setEnabled(4L, true);
    }

    @Test
    void setEnabled_nonAdmin_returns403WithoutCallingService() {
        ResponseEntity<?> resp = controller.setEnabled(4L, Map.of("enabled", false), customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(userService, never()).setEnabled(anyLong(), anyBoolean());
    }
}
