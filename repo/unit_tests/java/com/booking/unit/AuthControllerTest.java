package com.booking.unit;

import com.booking.controller.AuthController;
import com.booking.domain.User;
import com.booking.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthController — validates login/register field guards,
 * session-fixation protection on login, me endpoint, and logout behaviour.
 * Uses direct controller instantiation; no Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;
    @InjectMocks AuthController controller;

    private User adminUser;
    private User customerUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setEmail("admin@example.com");
        adminUser.setFullName("Admin User");
        adminUser.setRoleName("ADMINISTRATOR");

        customerUser = new User();
        customerUser.setId(4L);
        customerUser.setUsername("cust1");
        customerUser.setEmail("cust1@example.com");
        customerUser.setFullName("Customer One");
        customerUser.setRoleName("CUSTOMER");
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_missingUsername_returns400WithError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, String> body = new HashMap<>();
        body.put("password", "pass");

        ResponseEntity<?> resp = controller.login(body, request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertNotNull(rb.get("error"), "400 body must contain an error key");
    }

    @Test
    void login_missingPassword_returns400() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");

        ResponseEntity<?> resp = controller.login(body, request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void login_invalidCredentials_returns401WithErrorMessage() {
        when(authService.authenticate("admin", "wrong")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, String> body = Map.of("username", "admin", "password", "wrong");

        ResponseEntity<?> resp = controller.login(body, request);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertEquals("Invalid credentials", rb.get("error"));
    }

    @Test
    void login_validCredentials_returns200WithAllUserFields() {
        when(authService.authenticate("admin", "password123")).thenReturn(adminUser);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, String> body = Map.of("username", "admin", "password", "password123");

        ResponseEntity<?> resp = controller.login(body, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertEquals("admin",         rb.get("username"));
        assertEquals("ADMINISTRATOR", rb.get("role"));
        assertEquals("admin@example.com", rb.get("email"));
        assertEquals("Admin User",    rb.get("fullName"));
        assertNotNull(rb.get("id"),   "Response must include id");
        assertFalse(rb.containsKey("passwordHash"), "Response must never expose passwordHash");
    }

    @Test
    void login_validCredentials_invalidatesOldSession() {
        when(authService.authenticate("admin", "password123")).thenReturn(adminUser);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession oldSession = new MockHttpSession();
        request.setSession(oldSession);
        Map<String, String> body = Map.of("username", "admin", "password", "password123");

        controller.login(body, request);

        assertTrue(oldSession.isInvalid(),
                "Old session must be invalidated on login (session-fixation protection)");
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_blankUsername_returns400MentioningUsername() {
        MockHttpSession session = new MockHttpSession();
        Map<String, String> body = new HashMap<>();
        body.put("username", "");
        body.put("email", "a@b.com");
        body.put("password", "pass123");
        body.put("fullName", "Name");

        ResponseEntity<?> resp = controller.register(body, session);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertTrue(rb.get("error").toString().toLowerCase().contains("username"),
                "Error must mention 'username' when it is blank");
    }

    @Test
    void register_invalidEmail_returns400MentioningEmail() {
        MockHttpSession session = new MockHttpSession();
        Map<String, String> body = new HashMap<>();
        body.put("username", "newuser");
        body.put("email",    "not-an-email");
        body.put("password", "pass123");
        body.put("fullName", "Name");

        ResponseEntity<?> resp = controller.register(body, session);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertTrue(rb.get("error").toString().toLowerCase().contains("email"),
                "Error must mention 'email' for malformed email");
    }

    @Test
    void register_shortPassword_returns400() {
        MockHttpSession session = new MockHttpSession();
        Map<String, String> body = new HashMap<>();
        body.put("username", "newuser");
        body.put("email",    "new@example.com");
        body.put("password", "abc");   // < 6 chars
        body.put("fullName", "Name");

        ResponseEntity<?> resp = controller.register(body, session);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertTrue(rb.get("error").toString().contains("6"),
                "Error must mention the minimum length (6)");
    }

    @Test
    void register_blankFullName_returns400() {
        MockHttpSession session = new MockHttpSession();
        Map<String, String> body = new HashMap<>();
        body.put("username", "newuser");
        body.put("email",    "new@example.com");
        body.put("password", "pass123");
        body.put("fullName", "   ");

        ResponseEntity<?> resp = controller.register(body, session);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void register_validData_returns200WithUserIdAndMessage() {
        MockHttpSession session = new MockHttpSession();
        when(authService.register(eq("newuser"), eq("new@example.com"), eq("pass123"),
                eq("New User"), any(), any()))
                .thenReturn(customerUser);
        Map<String, String> body = new HashMap<>();
        body.put("username", "newuser");
        body.put("email",    "new@example.com");
        body.put("password", "pass123");
        body.put("fullName", "New User");

        ResponseEntity<?> resp = controller.register(body, session);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertNotNull(rb.get("userId"),  "Register response must include userId");
        assertNotNull(rb.get("message"), "Register response must include message");
    }

    @Test
    void register_duplicateUsername_returns400WithServiceMessage() {
        MockHttpSession session = new MockHttpSession();
        when(authService.register(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Username already exists"));
        Map<String, String> body = new HashMap<>();
        body.put("username", "admin");
        body.put("email",    "dup@example.com");
        body.put("password", "pass123");
        body.put("fullName", "Dup User");

        ResponseEntity<?> resp = controller.register(body, session);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertTrue(rb.get("error").toString().contains("Username already exists"));
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_invalidatesSessionAndReturnsMessage() {
        MockHttpSession session = new MockHttpSession();

        ResponseEntity<?> resp = controller.logout(session);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(session.isInvalid(), "Session must be invalidated on logout");
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertNotNull(rb.get("message"), "Logout response must include a message");
    }

    // ── me ────────────────────────────────────────────────────────────────────

    @Test
    void me_withoutCurrentUser_returns401() {
        MockHttpSession session = new MockHttpSession();  // no user set

        ResponseEntity<?> resp = controller.currentUser(session);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void me_withCurrentUser_returnsAllUserFields() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", adminUser);

        ResponseEntity<?> resp = controller.currentUser(session);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertEquals("admin",             rb.get("username"));
        assertEquals("ADMINISTRATOR",     rb.get("role"));
        assertEquals("admin@example.com", rb.get("email"));
        assertEquals("Admin User",        rb.get("fullName"));
        assertEquals(1L,                  rb.get("id"));
    }

    @Test
    void me_responseDoesNotExposePasswordHash() {
        MockHttpSession session = new MockHttpSession();
        adminUser.setPasswordHash("$2a$10$hashedvalue");
        session.setAttribute("currentUser", adminUser);

        ResponseEntity<?> resp = controller.currentUser(session);

        @SuppressWarnings("unchecked")
        Map<String, ?> rb = (Map<String, ?>) resp.getBody();
        assertFalse(rb.containsKey("passwordHash"),
                "Response must never expose passwordHash");
        assertFalse(rb.containsKey("password"),
                "Response must never expose a raw password field");
    }
}
