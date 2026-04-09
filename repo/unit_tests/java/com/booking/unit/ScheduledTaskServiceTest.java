package com.booking.unit;

import com.booking.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledTaskServiceTest {

    @Mock OrderService orderService;
    @Mock BlacklistService blacklistService;
    @Mock IdempotencyService idempotencyService;
    @InjectMocks ScheduledTaskService scheduledTaskService;

    @Test void closeUnpaidOrdersCallsService() {
        scheduledTaskService.closeUnpaidOrders();
        verify(orderService).autoCloseUnpaidOrders();
    }

    @Test void closeUnpaidOrdersSwallowsExceptions() {
        doThrow(new RuntimeException("boom")).when(orderService).autoCloseUnpaidOrders();
        scheduledTaskService.closeUnpaidOrders(); // should not throw
    }

    @Test void liftExpiredCallsService() {
        scheduledTaskService.liftExpiredBlacklists();
        verify(blacklistService).autoLiftExpired();
    }

    @Test void liftExpiredSwallowsExceptions() {
        doThrow(new RuntimeException("boom")).when(blacklistService).autoLiftExpired();
        scheduledTaskService.liftExpiredBlacklists(); // should not throw
    }

    @Test void cleanupTokensCallsService() {
        scheduledTaskService.cleanupIdempotencyTokens();
        verify(idempotencyService).cleanupExpired();
    }

    @Test void cleanupTokensSwallowsExceptions() {
        doThrow(new RuntimeException("boom")).when(idempotencyService).cleanupExpired();
        scheduledTaskService.cleanupIdempotencyTokens(); // should not throw
    }
}
