package com.booking.unit;

import com.booking.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleSecurityReturns403WithErrorBody() {
        ResponseEntity<?> r = handler.handleSecurity(new SecurityException("access denied"));
        assertEquals(403, r.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.getBody();
        assertNotNull(body, "Response body must not be null for security errors");
        assertEquals("access denied", body.get("error"),
                "Error body must contain the exception message under the 'error' key");
    }

    @Test
    void handleBadRequestReturns400WithErrorBody() {
        ResponseEntity<?> r = handler.handleBadRequest(new IllegalArgumentException("invalid input"));
        assertEquals(400, r.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.getBody();
        assertNotNull(body, "Response body must not be null for bad request errors");
        assertEquals("invalid input", body.get("error"),
                "Error body must contain the exception message under the 'error' key");
    }

    @Test
    void handleConflictReturns409WithErrorBody() {
        ResponseEntity<?> r = handler.handleConflict(new IllegalStateException("state conflict"));
        assertEquals(409, r.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.getBody();
        assertNotNull(body, "Response body must not be null for conflict errors");
        assertEquals("state conflict", body.get("error"),
                "Error body must contain the exception message under the 'error' key");
    }

    @Test
    void errorBodyContainsOnlyTheErrorKey() {
        // Callers should not receive unexpected fields; guard against future handler bloat
        ResponseEntity<?> r = handler.handleBadRequest(new IllegalArgumentException("check fields"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("error"), "Body must contain 'error' key");
        assertEquals(1, body.size(), "Body must contain exactly one field ('error')");
    }

    @Test
    void securityExceptionMessageIsPreservedVerbatim() {
        String specificMessage = "User is blacklisted and cannot perform this action";
        ResponseEntity<?> r = handler.handleSecurity(new SecurityException(specificMessage));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.getBody();
        assertNotNull(body);
        assertEquals(specificMessage, body.get("error"),
                "The full exception message must be forwarded to the caller unchanged");
    }
}
