package com.booking.service;

import com.booking.domain.NotificationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local dispatcher that logs and succeeds. In production, swap this for an
 * SMTP/SMS gateway implementation.
 */
@Component
public class LocalNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LocalNotificationDispatcher.class);

    @Override
    public boolean dispatch(NotificationRecord record) {
        log.debug("Dispatching {} to user {} via {}: {}",
                record.getReferenceType(), record.getUserId(),
                record.getChannel(), record.getSubject());
        return true;
    }
}
