package com.booking.service;

import com.booking.domain.PointsLedgerEntry;
import com.booking.mapper.PointsLedgerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PointsService {

    private final PointsLedgerMapper ledgerMapper;

    public PointsService(PointsLedgerMapper ledgerMapper) {
        this.ledgerMapper = ledgerMapper;
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
