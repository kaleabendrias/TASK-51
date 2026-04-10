package com.booking.controller;

import com.booking.domain.PhotographerDto;
import com.booking.domain.User;
import com.booking.service.UserService;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        if (!SessionUtil.isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        return ResponseEntity.ok(userService.getAll());
    }

    @GetMapping("/photographers")
    public ResponseEntity<?> photographers() {
        List<PhotographerDto> dtos = userService.getPhotographers().stream()
                .map(PhotographerDto::new)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpSession session) {
        User currentUser = SessionUtil.getCurrentUser(session);
        if (!currentUser.getId().equals(id) && !SessionUtil.isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        User user = userService.getById(id);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patchUpdate(@PathVariable Long id, @RequestBody Map<String, Object> fields,
                                         HttpSession session) {
        if (!SessionUtil.isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            userService.patchUpdate(id, fields);
            return ResponseEntity.ok(Map.of("message", "User updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> fields,
                                    HttpSession session) {
        if (!SessionUtil.isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            userService.patchUpdate(id, fields);
            return ResponseEntity.ok(Map.of("message", "User updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<?> setEnabled(@PathVariable Long id,
                                        @RequestBody Map<String, Boolean> body,
                                        HttpSession session) {
        if (!SessionUtil.isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            userService.setEnabled(id, body.get("enabled"));
            return ResponseEntity.ok(Map.of("message", "User status updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
