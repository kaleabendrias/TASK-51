package com.booking.controller;

import com.booking.domain.PointsAdjustment;
import com.booking.domain.PointsRule;
import com.booking.domain.User;
import com.booking.mapper.PointsAdjustmentMapper;
import com.booking.mapper.PointsLedgerMapper;
import com.booking.mapper.PointsRuleMapper;
import com.booking.service.PointsService;
import com.booking.util.RoleGuard;
import com.booking.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final PointsService pointsService;
    private final PointsRuleMapper rulesMapper;
    private final PointsAdjustmentMapper adjustmentMapper;
    private final PointsLedgerMapper ledgerMapper;

    public PointsController(PointsService pointsService, PointsRuleMapper rulesMapper,
                            PointsAdjustmentMapper adjustmentMapper, PointsLedgerMapper ledgerMapper) {
        this.pointsService = pointsService;
        this.rulesMapper = rulesMapper;
        this.adjustmentMapper = adjustmentMapper;
        this.ledgerMapper = ledgerMapper;
    }

    @GetMapping("/balance")
    public ResponseEntity<?> balance(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        return ResponseEntity.ok(Map.of("balance", pointsService.getBalance(user.getId())));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(HttpSession session) {
        User user = SessionUtil.getCurrentUser(session);
        return ResponseEntity.ok(pointsService.getHistory(user.getId()));
    }

    // ---- Points Rules (admin) ----
    @GetMapping("/rules")
    public ResponseEntity<?> listRules(HttpSession session) {
        RoleGuard.requireAdmin(session);
        return ResponseEntity.ok(rulesMapper.findAll());
    }

    @PostMapping("/rules")
    public ResponseEntity<?> createRule(@RequestBody PointsRule rule, HttpSession session) {
        RoleGuard.requireAdmin(session);
        if (rule.getActive() == null) rule.setActive(true);
        rulesMapper.insert(rule);
        return ResponseEntity.ok(rulesMapper.findById(rule.getId()));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody PointsRule rule,
                                        HttpSession session) {
        RoleGuard.requireAdmin(session);
        rule.setId(id);
        rulesMapper.update(rule);
        return ResponseEntity.ok(rulesMapper.findById(id));
    }

    // ---- Manual Adjustment with mandatory note + audit ----
    @PostMapping("/adjust")
    public ResponseEntity<?> adjust(@RequestBody Map<String, Object> body, HttpSession session) {
        User admin = RoleGuard.requireAdmin(session);
        Long userId = ((Number) body.get("userId")).longValue();
        int pts = ((Number) body.get("points")).intValue();
        String reason = (String) body.get("reason");

        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reason is mandatory for adjustments"));
        }

        int balanceBefore = pointsService.getBalance(userId);

        if (pts > 0) {
            pointsService.awardPoints(userId, pts, "ADMIN_ADJUST", "ADMIN", admin.getId(), reason);
        } else {
            pointsService.deductPoints(userId, Math.abs(pts), "ADMIN_ADJUST", "ADMIN", admin.getId(), reason);
        }

        int balanceAfter = pointsService.getBalance(userId);

        // Immutable audit record
        PointsAdjustment adj = new PointsAdjustment();
        adj.setUserId(userId);
        adj.setAdjustedBy(admin.getId());
        adj.setPoints(pts);
        adj.setReason(reason);
        adj.setBalanceBefore(balanceBefore);
        adj.setBalanceAfter(balanceAfter);
        adjustmentMapper.insert(adj);

        return ResponseEntity.ok(Map.of("balanceBefore", balanceBefore, "balanceAfter", balanceAfter,
                "adjustment", pts, "reason", reason));
    }

    @GetMapping("/adjustments")
    public ResponseEntity<?> listAdjustments(HttpSession session) {
        RoleGuard.requireAdmin(session);
        return ResponseEntity.ok(adjustmentMapper.findAll());
    }

    // ---- Leaderboard: sorted by points desc, tie-break by completion count, then earliest completion ----
    @GetMapping("/leaderboard")
    public ResponseEntity<?> leaderboard(HttpSession session) {
        SessionUtil.getCurrentUser(session);
        return ResponseEntity.ok(pointsService.getLeaderboard());
    }

    // ---- Award (legacy compat) ----
    @PostMapping("/award")
    public ResponseEntity<?> award(@RequestBody Map<String, Object> body, HttpSession session) {
        User admin = RoleGuard.requireAdmin(session);
        try {
            Long userId = ((Number) body.get("userId")).longValue();
            int pts = ((Number) body.get("points")).intValue();
            String description = (String) body.get("description");
            return ResponseEntity.ok(pointsService.awardPoints(userId, pts,
                    "ADMIN_AWARD", "ADMIN", admin.getId(), description));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
