package com.booking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Legacy /api/attachments surface — removed.
 * File attachments are now handled through the chat/messaging system
 * via /api/messages/conversations/{id}/image.
 * Returns 410 GONE directing clients to the messaging API.
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private static final Map<String, String> GONE_BODY =
            Map.of("error", "The /api/attachments endpoint has been removed. Use /api/messages for file sharing.");

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<?> listForBooking(@PathVariable Long bookingId) {
        return ResponseEntity.status(410).body(GONE_BODY);
    }

    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<?> upload(@PathVariable Long bookingId) {
        return ResponseEntity.status(410).body(GONE_BODY);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        return ResponseEntity.status(410).body(GONE_BODY);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return ResponseEntity.status(410).body(GONE_BODY);
    }
}
