package com.booking.controller;

import com.booking.domain.Booking;
import com.booking.domain.User;
import com.booking.service.BookingService;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<List<Booking>> list(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        return ResponseEntity.ok(bookingService.getForUser(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        Booking booking = bookingService.getById(id);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        if (!bookingService.canUserAccess(booking, user)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        return ResponseEntity.ok(booking);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Booking booking, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            if ("CUSTOMER".equals(user.getRoleName())) {
                booking.setCustomerId(user.getId());
            }
            Booking created = bookingService.create(booking);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Booking booking,
                                    HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            booking.setId(id);
            Booking updated = bookingService.update(booking, user);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body,
                                          HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            bookingService.updateStatus(id, body.get("status"), user);
            return ResponseEntity.ok(Map.of("message", "Status updated"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
