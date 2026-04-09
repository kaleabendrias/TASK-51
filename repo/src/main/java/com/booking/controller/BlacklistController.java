package com.booking.controller;

import com.booking.domain.User;
import com.booking.service.BlacklistService;
import com.booking.util.RoleGuard;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/blacklist")
public class BlacklistController {

    private final BlacklistService blacklistService;

    public BlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        RoleGuard.requireAdmin(session);
        return ResponseEntity.ok(blacklistService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpSession session) {
        RoleGuard.requireAdmin(session);
        var entry = blacklistService.getById(id);
        if (entry == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(entry);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> checkUser(@PathVariable Long userId, HttpSession session) {
        RoleGuard.requireAdmin(session);
        var entry = blacklistService.getActiveByUser(userId);
        if (entry == null) return ResponseEntity.ok(Map.of("blacklisted", false));
        return ResponseEntity.ok(Map.of("blacklisted", !entry.isExpired(), "entry", entry));
    }

    @PostMapping
    public ResponseEntity<?> blacklist(@RequestBody Map<String, Object> body, HttpSession session) {
        User admin = RoleGuard.requireAdmin(session);
        try {
            Long userId = ((Number) body.get("userId")).longValue();
            String reason = (String) body.get("reason");
            Integer duration = body.get("durationDays") != null ?
                    ((Number) body.get("durationDays")).intValue() : null;

            return ResponseEntity.ok(blacklistService.blacklist(userId, reason, duration, admin));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/lift")
    public ResponseEntity<?> lift(@PathVariable Long id, @RequestBody Map<String, String> body,
                                  HttpSession session) {
        User admin = RoleGuard.requireAdmin(session);
        try {
            return ResponseEntity.ok(blacklistService.lift(id, body.get("reason"), admin));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
