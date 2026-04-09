package com.booking.unit;

import com.booking.domain.PointsLedgerEntry;
import com.booking.mapper.PointsLedgerMapper;
import com.booking.service.PointsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsServiceTest {

    @Mock PointsLedgerMapper ledgerMapper;
    @InjectMocks PointsService pointsService;

    @Test void awardPointsAddsToBalance() {
        when(ledgerMapper.getBalance(4L)).thenReturn(50);
        PointsLedgerEntry entry = pointsService.awardPoints(4L, 20, "TEST", "ORDER", 1L, "Bonus");
        assertEquals(20, entry.getPoints());
        assertEquals(70, entry.getBalanceAfter());
        verify(ledgerMapper).insert(any());
        verify(ledgerMapper).updateUserBalance(4L, 70);
    }

    @Test void deductPointsSubtracts() {
        when(ledgerMapper.getBalance(4L)).thenReturn(50);
        PointsLedgerEntry entry = pointsService.deductPoints(4L, 30, "DEDUCT", "REF", 1L, "Refund");
        assertEquals(-30, entry.getPoints());
        assertEquals(20, entry.getBalanceAfter());
        verify(ledgerMapper).updateUserBalance(4L, 20);
    }

    @Test void deductBelowZeroClampsToZero() {
        when(ledgerMapper.getBalance(4L)).thenReturn(10);
        PointsLedgerEntry entry = pointsService.deductPoints(4L, 50, "DEDUCT", "REF", 1L, "Big refund");
        assertEquals(0, entry.getBalanceAfter());
        verify(ledgerMapper).updateUserBalance(4L, 0);
    }

    @Test void getBalanceDelegates() {
        when(ledgerMapper.getBalance(4L)).thenReturn(99);
        assertEquals(99, pointsService.getBalance(4L));
    }

    @Test void getHistoryDelegates() {
        when(ledgerMapper.findByUserId(4L)).thenReturn(List.of(new PointsLedgerEntry()));
        assertEquals(1, pointsService.getHistory(4L).size());
    }

    @Test void getLeaderboardDelegates() {
        when(ledgerMapper.getLeaderboard()).thenReturn(List.of(Map.of("userId", 4L, "points", 100)));
        assertEquals(1, pointsService.getLeaderboard().size());
    }
}
