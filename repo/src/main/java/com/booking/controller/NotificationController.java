package com.booking.controller;

import com.booking.domain.NotificationPreference;
import com.booking.domain.User;
import com.booking.mapper.NotificationPreferenceMapper;
import com.booking.service.NotificationService;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceMapper prefMapper;

    public NotificationController(NotificationService notificationService,
                                  NotificationPreferenceMapper prefMapper) {
        this.notificationService = notificationService;
        this.prefMapper = prefMapper;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        return ResponseEntity.ok(notificationService.getByUser(user.getId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        notificationService.markRead(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        notificationService.archive(id, user.getId());
        return ResponseEntity.ok(Map.of("message", "Archived"));
    }

    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        NotificationPreference pref = prefMapper.findByUserId(user.getId());
        if (pref == null) {
            pref = new NotificationPreference();
            pref.setUserId(user.getId());
            pref.setOrderUpdates(true);
            pref.setHolds(true);
            pref.setReminders(true);
            pref.setApprovals(true);
            pref.setCompliance(true);
            pref.setMuteNonCritical(false);
            prefMapper.insert(pref);
        }
        return ResponseEntity.ok(pref);
    }

    @PutMapping("/preferences")
    public ResponseEntity<?> updatePreferences(@RequestBody NotificationPreference pref,
                                                HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        pref.setUserId(user.getId());
        // Compliance is always true — cannot be muted
        pref.setCompliance(true);
        NotificationPreference existing = prefMapper.findByUserId(user.getId());
        if (existing == null) {
            prefMapper.insert(pref);
        } else {
            prefMapper.update(pref);
        }
        return ResponseEntity.ok(prefMapper.findByUserId(user.getId()));
    }
}
