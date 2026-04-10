package com.booking.controller;

import com.booking.domain.Order;
import com.booking.domain.User;
import com.booking.service.IdempotencyService;
import com.booking.service.IdempotencyService.IdempotencyResult;
import com.booking.service.OrderService;
import com.booking.util.SessionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public OrderController(OrderService orderService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        return ResponseEntity.ok(orderService.getForUser(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        Order order = orderService.getById(id);
        if (order == null) return ResponseEntity.notFound().build();
        if (!orderService.canUserAccess(order, user))
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<?> audit(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        Order order = orderService.getById(id);
        if (order == null) return ResponseEntity.notFound().build();
        if (!orderService.canUserAccess(order, user))
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        return ResponseEntity.ok(orderService.getAuditTrail(id));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
                                    HttpSession session) {
        String action = "CREATE_ORDER";
        IdempotencyResult idem = idempotencyService.checkToken(idempotencyKey, action, null);
        if (idem.isDuplicate) {
            return ResponseEntity.status(idem.cachedStatus).body(idem.cachedBody);
        }

        User user = SessionUtil.getCurrentUser(session);
        try {
            Long listingId = ((Number) body.get("listingId")).longValue();
            Long timeSlotId = ((Number) body.get("timeSlotId")).longValue();
            Long addressId = body.get("addressId") != null ? ((Number) body.get("addressId")).longValue() : null;
            String notes = (String) body.get("notes");
            String deliveryMode = (String) body.get("deliveryMode");

            Order order = orderService.createOrder(listingId, timeSlotId, addressId, notes, deliveryMode, user);

            String responseBody = objectMapper.writeValueAsString(order);
            // For CREATE, orderId was null at check time — use null for consistent scoped key
            idempotencyService.recordResponse(idempotencyKey, action, null, 200, responseBody);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException | IllegalStateException e) {
            idempotencyService.recordResponse(idempotencyKey, action, null, 400, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            idempotencyService.recordResponse(idempotencyKey, action, null, 403, e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            idempotencyService.recordResponse(idempotencyKey, action, null, 500, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Order creation failed"));
        }
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id,
                                     @RequestHeader(value = "Idempotency-Key") String key,
                                     HttpSession session) {
        return executeAction(key, "CONFIRM", id, session, (order, user) -> orderService.confirm(id, user));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<?> pay(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                 @RequestHeader(value = "Idempotency-Key") String key,
                                 HttpSession session) {
        return executeAction(key, "PAY", id, session, (order, user) -> {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String ref = (String) body.get("paymentReference");
            return orderService.recordPayment(id, amount, ref, user);
        });
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<?> checkIn(@PathVariable Long id,
                                     @RequestHeader(value = "Idempotency-Key") String key,
                                     HttpSession session) {
        return executeAction(key, "CHECK_IN", id, session, (order, user) -> orderService.checkIn(id, user));
    }

    @PostMapping("/{id}/check-out")
    public ResponseEntity<?> checkOut(@PathVariable Long id,
                                      @RequestHeader(value = "Idempotency-Key") String key,
                                      HttpSession session) {
        return executeAction(key, "CHECK_OUT", id, session, (order, user) -> orderService.checkOut(id, user));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable Long id,
                                      @RequestHeader(value = "Idempotency-Key") String key,
                                      HttpSession session) {
        return executeAction(key, "COMPLETE", id, session, (order, user) -> orderService.complete(id, user));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, @RequestBody Map<String, String> body,
                                    @RequestHeader(value = "Idempotency-Key") String key,
                                    HttpSession session) {
        return executeAction(key, "CANCEL", id, session,
                (order, user) -> orderService.cancel(id, body.get("reason"), user));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refund(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Idempotency-Key") String key,
                                    HttpSession session) {
        return executeAction(key, "REFUND", id, session, (order, user) -> {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String reason = (String) body.get("reason");
            return orderService.refund(id, amount, reason, user);
        });
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<?> reschedule(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "Idempotency-Key") String key,
                                        HttpSession session) {
        return executeAction(key, "RESCHEDULE", id, session, (order, user) -> {
            Long newSlotId = ((Number) body.get("newTimeSlotId")).longValue();
            return orderService.reschedule(id, newSlotId, user);
        });
    }

    @FunctionalInterface
    private interface OrderAction {
        Order execute(Order order, User user);
    }

    private ResponseEntity<?> executeAction(String idempotencyKey, String action,
                                            Long orderId, HttpSession session,
                                            OrderAction orderAction) {
        IdempotencyResult idem = idempotencyService.checkToken(idempotencyKey, action, orderId);
        if (idem.isDuplicate) {
            return ResponseEntity.status(idem.cachedStatus).body(idem.cachedBody);
        }

        User user = SessionUtil.getCurrentUser(session);
        try {
            Order order = orderService.getById(orderId);
            if (order == null) {
                idempotencyService.recordResponse(idempotencyKey, action, orderId, 404, "Order not found");
                return ResponseEntity.notFound().build();
            }
            if (!orderService.canUserAccess(order, user)) {
                idempotencyService.recordResponse(idempotencyKey, action, orderId, 403, "Access denied");
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }

            Order result = orderAction.execute(order, user);
            String body = objectMapper.writeValueAsString(result);
            idempotencyService.recordResponse(idempotencyKey, action, orderId, 200, body);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            idempotencyService.recordResponse(idempotencyKey, action, orderId, 400, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            idempotencyService.recordResponse(idempotencyKey, action, orderId, 403, e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            idempotencyService.recordResponse(idempotencyKey, action, orderId, 500, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Operation failed"));
        }
    }
}
