package com.booking.controller;

import com.booking.service.PhotoServiceService;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final PhotoServiceService photoServiceService;

    public ServiceController(PhotoServiceService photoServiceService) {
        this.photoServiceService = photoServiceService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(photoServiceService.getActive());
    }

    @GetMapping("/all")
    public ResponseEntity<?> listAll(HttpSession session) {
        if (!SessionUtil.isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        return ResponseEntity.ok(photoServiceService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        com.booking.domain.Service service = photoServiceService.getById(id);
        if (service == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody com.booking.domain.Service service,
                                    HttpSession session) {
        if (!SessionUtil.isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            return ResponseEntity.ok(photoServiceService.create(service));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody com.booking.domain.Service service,
                                    HttpSession session) {
        if (!SessionUtil.isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            service.setId(id);
            return ResponseEntity.ok(photoServiceService.update(service));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
