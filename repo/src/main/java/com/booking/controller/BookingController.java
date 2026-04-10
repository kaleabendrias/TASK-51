package com.booking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Legacy /api/bookings surface — removed.
 * All lifecycle logic is unified under /api/orders FSM.
 * Returns 410 GONE directing clients to the orders API.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final Map<String, String> GONE_BODY =
            Map.of("error", "The /api/bookings endpoint has been removed. Use /api/orders instead.");

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.status(410).body(GONE_BODY); }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.status(410).body(GONE_BODY); }

    @PostMapping
    public ResponseEntity<?> create() { return ResponseEntity.status(410).body(GONE_BODY); }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id) { return ResponseEntity.status(410).body(GONE_BODY); }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id) { return ResponseEntity.status(410).body(GONE_BODY); }
}
