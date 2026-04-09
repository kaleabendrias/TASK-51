package com.booking.service;

import com.booking.domain.NotificationRecord;
import com.booking.domain.Order;
import com.booking.domain.User;
import com.booking.mapper.NotificationMapper;
import com.booking.mapper.UserMapper;
import com.booking.util.FieldEncryptor;
import com.booking.util.MaskUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final int MAX_RETRIES = 3;

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    public NotificationService(NotificationMapper notificationMapper, UserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
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

    public void queueEmail(Long userId, String subject, String body,
                           String refType, Long refId) {
        User user = userMapper.findById(userId);
        if (user == null || user.getEmail() == null) return;

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
