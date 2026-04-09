package com.booking.unit;

import com.booking.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void handleSecurity() {
        ResponseEntity<?> r = handler.handleSecurity(new SecurityException("denied"));
        assertEquals(403, r.getStatusCode().value());
    }

    @Test void handleBadRequest() {
        ResponseEntity<?> r = handler.handleBadRequest(new IllegalArgumentException("bad"));
        assertEquals(400, r.getStatusCode().value());
    }

    @Test void handleConflict() {
        ResponseEntity<?> r = handler.handleConflict(new IllegalStateException("conflict"));
        assertEquals(409, r.getStatusCode().value());
    }
}
