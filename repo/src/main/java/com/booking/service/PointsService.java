package com.booking.service;

import com.booking.domain.PointsLedgerEntry;
import com.booking.domain.PointsRule;
import com.booking.domain.User;
import com.booking.mapper.PointsLedgerMapper;
import com.booking.mapper.PointsRuleMapper;
import com.booking.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PointsService {

    private final PointsLedgerMapper ledgerMapper;
    private final PointsRuleMapper ruleMapper;
    private final UserMapper userMapper;

    public PointsService(PointsLedgerMapper ledgerMapper, PointsRuleMapper ruleMapper,
                         UserMapper userMapper) {
        this.ledgerMapper = ledgerMapper;
        this.ruleMapper = ruleMapper;
        this.userMapper = userMapper;
    }

    public int getBalance(Long userId) {
        return ledgerMapper.getBalance(userId);
    }

    public List<PointsLedgerEntry> getHistory(Long userId) {
        return ledgerMapper.findByUserId(userId);
    }

    public List<Map<String, Object>> getLeaderboard() {
        return ledgerMapper.getLeaderboard();
    }

    /**
     * Award points to a user by trigger event, respecting the configured rule scope.
     * INDIVIDUAL: awards to the specific user.
     * DEPARTMENT/TEAM: awards to all users sharing the same department/team.
     */
    @Transactional
    public void awardByTrigger(String triggerEvent, Long userId, String refType, Long refId, String context) {
        PointsRule rule = ruleMapper.findByTrigger(triggerEvent);
        if (rule == null || !rule.getActive()) return;

        switch (rule.getScope()) {
            case "INDIVIDUAL" -> awardPoints(userId, rule.getPoints(), rule.getName(), refType, refId,
                    rule.getDescription() + " " + context);
            case "DEPARTMENT" -> {
                User user = userMapper.findById(userId);
                if (user != null && user.getDepartment() != null) {
                    List<User> deptUsers = userMapper.findByDepartment(user.getDepartment());
                    for (User u : deptUsers) {
                        awardPoints(u.getId(), rule.getPoints(), rule.getName(), refType, refId,
                                rule.getDescription() + " (dept: " + user.getDepartment() + ") " + context);
                    }
                } else {
                    awardPoints(userId, rule.getPoints(), rule.getName(), refType, refId,
                            rule.getDescription() + " " + context);
                }
            }
            case "TEAM" -> {
                User user = userMapper.findById(userId);
                if (user != null && user.getTeam() != null) {
                    List<User> teamUsers = userMapper.findByTeam(user.getTeam());
                    for (User u : teamUsers) {
                        awardPoints(u.getId(), rule.getPoints(), rule.getName(), refType, refId,
                                rule.getDescription() + " (team: " + user.getTeam() + ") " + context);
                    }
                } else {
                    awardPoints(userId, rule.getPoints(), rule.getName(), refType, refId,
                            rule.getDescription() + " " + context);
                }
            }
            case "CLASS" -> {
                // CLASS scope: awards to all users sharing the same role
                User user = userMapper.findById(userId);
                if (user != null && user.getRoleId() != null) {
                    List<User> classUsers = userMapper.findByRoleId(user.getRoleId());
                    for (User u : classUsers) {
                        awardPoints(u.getId(), rule.getPoints(), rule.getName(), refType, refId,
                                rule.getDescription() + " (class: " + user.getRoleName() + ") " + context);
                    }
                } else {
                    awardPoints(userId, rule.getPoints(), rule.getName(), refType, refId,
                            rule.getDescription() + " " + context);
                }
            }
            default -> awardPoints(userId, rule.getPoints(), rule.getName(), refType, refId,
                    rule.getDescription() + " " + context);
        }
    }

    @Transactional
    public PointsLedgerEntry awardPoints(Long userId, int points, String action,
                                         String refType, Long refId, String description) {
        int currentBalance = ledgerMapper.getBalance(userId);
        int newBalance = currentBalance + points;

        PointsLedgerEntry entry = new PointsLedgerEntry();
        entry.setUserId(userId);
        entry.setPoints(points);
        entry.setBalanceAfter(newBalance);
        entry.setAction(action);
        entry.setReferenceType(refType);
        entry.setReferenceId(refId);
        entry.setDescription(description);
        ledgerMapper.insert(entry);
        ledgerMapper.updateUserBalance(userId, newBalance);

        return entry;
    }

    @Transactional
    public PointsLedgerEntry deductPoints(Long userId, int points, String action,
                                          String refType, Long refId, String description) {
        int currentBalance = ledgerMapper.getBalance(userId);
        int newBalance = Math.max(0, currentBalance - points);

        PointsLedgerEntry entry = new PointsLedgerEntry();
        entry.setUserId(userId);
        entry.setPoints(-points);
        entry.setBalanceAfter(newBalance);
        entry.setAction(action);
        entry.setReferenceType(refType);
        entry.setReferenceId(refId);
        entry.setDescription(description);
        ledgerMapper.insert(entry);
        ledgerMapper.updateUserBalance(userId, newBalance);

        return entry;
    }
}
