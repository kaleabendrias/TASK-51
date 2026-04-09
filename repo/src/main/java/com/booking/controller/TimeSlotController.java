package com.booking.controller;

import com.booking.domain.TimeSlot;
import com.booking.domain.User;
import com.booking.service.TimeSlotService;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/timeslots")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    public TimeSlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @GetMapping("/listing/{listingId}")
    public ResponseEntity<?> byListing(@PathVariable Long listingId) {
        return ResponseEntity.ok(timeSlotService.getByListing(listingId));
    }

    @GetMapping("/listing/{listingId}/available")
    public ResponseEntity<?> available(@PathVariable Long listingId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(timeSlotService.getAvailable(listingId, start, end));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TimeSlot slot, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            return ResponseEntity.ok(timeSlotService.create(slot, user));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
