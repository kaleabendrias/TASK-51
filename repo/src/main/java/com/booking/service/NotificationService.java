package com.booking.service;

import com.booking.domain.NotificationPreference;
import com.booking.domain.NotificationRecord;
import com.booking.domain.Order;
import com.booking.domain.User;
import com.booking.mapper.NotificationMapper;
import com.booking.mapper.NotificationPreferenceMapper;
import com.booking.mapper.UserMapper;
import com.booking.util.FieldEncryptor;
import com.booking.util.MaskUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_RETRIES = 3;

    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceMapper prefMapper;
    private final UserMapper userMapper;
    private final NotificationDispatcher dispatcher;

    public NotificationService(NotificationMapper notificationMapper,
                               NotificationPreferenceMapper prefMapper,
                               UserMapper userMapper,
                               NotificationDispatcher dispatcher) {
        this.notificationMapper = notificationMapper;
        this.prefMapper = prefMapper;
        this.userMapper = userMapper;
        this.dispatcher = dispatcher;
    }

    public List<NotificationRecord> getByUser(Long userId) {
        List<NotificationRecord> records = notificationMapper.findByUserId(userId);
        for (NotificationRecord r : records) {
            r.setRecipient(maskRecipient(r.getChannel(), r.getRecipient()));
        }
        return records;
    }

    public void markRead(Long notificationId, Long userId) {
        NotificationRecord r = notificationMapper.findById(notificationId);
        if (r != null && r.getUserId().equals(userId)) {
            notificationMapper.markRead(notificationId);
        }
    }

    public void archive(Long notificationId, Long userId) {
        NotificationRecord r = notificationMapper.findById(notificationId);
        if (r != null && r.getUserId().equals(userId)) {
            notificationMapper.archive(notificationId);
        }
    }

    public void retryNotification(Long notificationId) {
        NotificationRecord r = notificationMapper.findById(notificationId);
        if (r == null) return;
        if (r.getRetryCount() != null && r.getRetryCount() >= MAX_RETRIES) {
            notificationMapper.markTerminal(notificationId);
            return;
        }
        notificationMapper.incrementRetry(notificationId);
        notificationMapper.updateStatus(notificationId, "QUEUED");
    }

    /**
     * True queue lifecycle: attempt dispatch, increment failure on error, mark terminal after max retries.
     */
    public void processRetryQueue() {
        List<NotificationRecord> queued = notificationMapper.findQueued();
        for (NotificationRecord r : queued) {
            boolean dispatched = attemptDispatch(r);
            if (dispatched) {
                notificationMapper.updateStatus(r.getId(), "READY_FOR_EXPORT");
            } else {
                notificationMapper.incrementRetry(r.getId());
                int newCount = (r.getRetryCount() != null ? r.getRetryCount() : 0) + 1;
                if (newCount >= MAX_RETRIES) {
                    notificationMapper.markTerminal(r.getId());
                    log.warn("Notification {} marked terminal after {} failures", r.getId(), newCount);
                } else {
                    notificationMapper.updateStatus(r.getId(), "RETRY");
                    log.info("Notification {} failed, retry {}/{}", r.getId(), newCount, MAX_RETRIES);
                }
            }
        }
    }

    private boolean attemptDispatch(NotificationRecord record) {
        return dispatcher.dispatch(record);
    }

    public List<NotificationRecord> getReadyForExport() {
        return notificationMapper.findByStatus("READY_FOR_EXPORT");
    }

    public int markExported(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            notificationMapper.updateStatus(id, "EXPORTED");
            count++;
        }
        return count;
    }

    /**
     * Queue email with mute preference enforcement.
     * Compliance notifications are never muted.
     */
    public void queueEmail(Long userId, String subject, String body,
                           String refType, Long refId) {
        User user = userMapper.findById(userId);
        if (user == null || user.getEmail() == null) return;

        // Enforce mute preferences (compliance is never muted)
        boolean isCompliance = "COMPLIANCE".equals(refType);
        if (!isCompliance && isMuted(userId, refType)) {
            return;
        }

        NotificationRecord record = new NotificationRecord();
        record.setUserId(userId);
        record.setChannel("EMAIL");
        record.setRecipient(FieldEncryptor.encrypt(user.getEmail()));
        record.setSubject(subject);
        record.setBody(body);
        record.setStatus("QUEUED");
        record.setReferenceType(refType);
        record.setReferenceId(refId);
        notificationMapper.insert(record);
    }

    public void queueSms(Long userId, String body, String refType, Long refId) {
        User user = userMapper.findById(userId);
        if (user == null || user.getPhone() == null) return;

        boolean isCompliance = "COMPLIANCE".equals(refType);
        if (!isCompliance && isMuted(userId, refType)) return;

        NotificationRecord record = new NotificationRecord();
        record.setUserId(userId);
        record.setChannel("SMS");
        record.setRecipient(FieldEncryptor.encrypt(user.getPhone()));
        record.setSubject(null);
        record.setBody(body);
        record.setStatus("QUEUED");
        record.setReferenceType(refType);
        record.setReferenceId(refId);
        notificationMapper.insert(record);
    }

    private boolean isMuted(Long userId, String refType) {
        NotificationPreference pref = prefMapper.findByUserId(userId);
        if (pref == null) return false;
        if (Boolean.TRUE.equals(pref.getMuteNonCritical())) return true;
        return switch (refType != null ? refType : "") {
            case "ORDER" -> !Boolean.TRUE.equals(pref.getOrderUpdates());
            case "HOLD" -> !Boolean.TRUE.equals(pref.getHolds());
            case "REMINDER", "OVERDUE" -> !Boolean.TRUE.equals(pref.getReminders());
            case "APPROVAL" -> !Boolean.TRUE.equals(pref.getApprovals());
            default -> false;
        };
    }

    // Template catalog for order state notifications
    private static final Map<String, String> TEMPLATES = Map.ofEntries(
        Map.entry("ORDER_CREATED",       "Your order %s has been created. Payment is due within 30 minutes."),
        Map.entry("ORDER_CONFIRMED",     "Order %s has been confirmed by the photographer."),
        Map.entry("PAYMENT_RECEIVED",    "Payment has been received for order %s."),
        Map.entry("ORDER_CHECKED_IN",    "Check-in recorded for order %s. Session is in progress."),
        Map.entry("ORDER_CHECKED_OUT",   "Check-out recorded for order %s."),
        Map.entry("ORDER_COMPLETED",     "Order %s is now complete. Thank you!"),
        Map.entry("ORDER_CANCELLED",     "Order %s has been cancelled."),
        Map.entry("ORDER_AUTO_CANCELLED","Order %s was automatically cancelled due to payment timeout."),
        Map.entry("ORDER_REFUNDED",      "A refund has been processed for order %s."),
        Map.entry("ORDER_RESCHEDULED",   "Order %s has been rescheduled to a new time slot.")
    );

    public void queueOrderNotification(Order order, String eventType, String message) {
        String body = TEMPLATES.getOrDefault(eventType, message);
        String formatted = String.format(body, order.getOrderNumber());
        String subject = eventType.replace('_', ' ') + ": " + order.getOrderNumber();
        queueEmail(order.getCustomerId(), subject, formatted, "ORDER", order.getId());
        queueEmail(order.getPhotographerId(), subject, formatted, "ORDER", order.getId());
    }

    public void queueHoldNotification(Long userId, String orderNumber, String reason) {
        queueEmail(userId, "HOLD: " + orderNumber,
                String.format("A hold has been placed on order %s. Reason: %s", orderNumber, reason),
                "HOLD", null);
    }

    public void queueOverdueNotification(Long userId, String orderNumber) {
        queueEmail(userId, "OVERDUE: " + orderNumber,
                String.format("Order %s payment is overdue. Please complete payment to avoid cancellation.", orderNumber),
                "OVERDUE", null);
    }

    public void queueApprovalNotification(Long userId, String orderNumber, String action) {
        queueEmail(userId, "APPROVAL REQUIRED: " + orderNumber,
                String.format("Order %s requires your approval for: %s", orderNumber, action),
                "APPROVAL", null);
    }

    private String maskRecipient(String channel, String encrypted) {
        if (encrypted == null) return "***";
        try {
            String decrypted = FieldEncryptor.decrypt(encrypted);
            return "EMAIL".equals(channel) ? MaskUtil.maskEmail(decrypted) : MaskUtil.maskPhone(decrypted);
        } catch (Exception e) {
            return "***";
        }
    }
}
