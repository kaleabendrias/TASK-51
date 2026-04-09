package com.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    private final OrderService orderService;
    private final BlacklistService blacklistService;
    private final IdempotencyService idempotencyService;

    public ScheduledTaskService(OrderService orderService,
                                BlacklistService blacklistService,
                                IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.blacklistService = blacklistService;
        this.idempotencyService = idempotencyService;
    }

    // Auto-close unpaid orders every 2 minutes
    @Scheduled(fixedRate = 120_000, initialDelay = 60_000)
    public void closeUnpaidOrders() {
        log.debug("Running auto-close unpaid orders task");
        try {
            orderService.autoCloseUnpaidOrders();
        } catch (Exception e) {
            log.error("Error in auto-close unpaid orders", e);
        }
    }

    // Auto-lift expired blacklist entries every 5 minutes
    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void liftExpiredBlacklists() {
        log.debug("Running auto-lift expired blacklists task");
        try {
            blacklistService.autoLiftExpired();
        } catch (Exception e) {
            log.error("Error in auto-lift blacklists", e);
        }
    }

    // Cleanup expired idempotency tokens every 15 minutes
    @Scheduled(fixedRate = 900_000, initialDelay = 120_000)
    public void cleanupIdempotencyTokens() {
        log.debug("Running idempotency token cleanup");
        try {
            idempotencyService.cleanupExpired();
        } catch (Exception e) {
            log.error("Error in idempotency cleanup", e);
        }
    }
}
