package com.booking.controller;

import com.booking.domain.Address;
import com.booking.domain.User;
import com.booking.service.AddressService;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        return ResponseEntity.ok(addressService.getByUser(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpSession session) {
        Address addr = addressService.getById(id);
        if (addr == null) return ResponseEntity.notFound().build();
        User user = SessionUtil.getCurrentUser(session);
        if (!addr.getUserId().equals(user.getId()) && !"ADMINISTRATOR".equals(user.getRoleName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        return ResponseEntity.ok(addr);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Address address, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            return ResponseEntity.ok(addressService.create(address, user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Address address,
                                    HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            address.setId(id);
            return ResponseEntity.ok(addressService.update(address, user));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        try {
            addressService.delete(id, user);
            return ResponseEntity.ok(Map.of("message", "Address deleted"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
