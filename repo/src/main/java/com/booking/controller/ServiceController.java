package com.booking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Legacy /api/services surface — removed.
 * Service catalog is now managed through the listings-based order FSM.
 * Returns 410 GONE directing clients to /api/listings.
 */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private static final Map<String, String> GONE_BODY =
            Map.of("error", "The /api/services endpoint has been removed. Use /api/listings instead.");

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.status(410).body(GONE_BODY); }

    @GetMapping("/all")
    public ResponseEntity<?> listAll() { return ResponseEntity.status(410).body(GONE_BODY); }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.status(410).body(GONE_BODY); }

    @PostMapping
    public ResponseEntity<?> create() { return ResponseEntity.status(410).body(GONE_BODY); }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id) { return ResponseEntity.status(410).body(GONE_BODY); }
}
