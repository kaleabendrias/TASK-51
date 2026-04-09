package com.booking.domain;

import java.time.LocalDateTime;

public class NotificationPreference {
    private Long id;
    private Long userId;
    private Boolean orderUpdates;
    private Boolean holds;
    private Boolean reminders;
    private Boolean approvals;
    private Boolean compliance;
    private Boolean muteNonCritical;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Boolean getOrderUpdates() { return orderUpdates; }
    public void setOrderUpdates(Boolean orderUpdates) { this.orderUpdates = orderUpdates; }
    public Boolean getHolds() { return holds; }
    public void setHolds(Boolean holds) { this.holds = holds; }
    public Boolean getReminders() { return reminders; }
    public void setReminders(Boolean reminders) { this.reminders = reminders; }
    public Boolean getApprovals() { return approvals; }
    public void setApprovals(Boolean approvals) { this.approvals = approvals; }
    public Boolean getCompliance() { return compliance; }
    public void setCompliance(Boolean compliance) { this.compliance = compliance; }
    public Boolean getMuteNonCritical() { return muteNonCritical; }
    public void setMuteNonCritical(Boolean muteNonCritical) { this.muteNonCritical = muteNonCritical; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
