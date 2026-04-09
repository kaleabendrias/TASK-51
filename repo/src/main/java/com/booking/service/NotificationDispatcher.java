package com.booking.service;

import com.booking.domain.NotificationRecord;

/**
 * Abstraction for notification dispatch. Production implementations connect to
 * email/SMS gateways. The default local implementation logs and succeeds.
 * Test implementations can inject deterministic failure scenarios.
 */
public interface NotificationDispatcher {
    boolean dispatch(NotificationRecord record);
}
