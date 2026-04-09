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

@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    public NotificationService(NotificationMapper notificationMapper, UserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
    }

    public List<NotificationRecord> getByUser(Long userId) {
        List<NotificationRecord> records = notificationMapper.findByUserId(userId);
        // Mask recipient in responses
        for (NotificationRecord r : records) {
            r.setRecipient(maskRecipient(r.getChannel(), r.getRecipient()));
        }
        return records;
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

    public void queueOrderNotification(Order order, String eventType, String message) {
        // Queue email for customer
        queueEmail(order.getCustomerId(), eventType + ": " + order.getOrderNumber(),
                message, "ORDER", order.getId());
        // Queue email for photographer
        queueEmail(order.getPhotographerId(), eventType + ": " + order.getOrderNumber(),
                message, "ORDER", order.getId());
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
