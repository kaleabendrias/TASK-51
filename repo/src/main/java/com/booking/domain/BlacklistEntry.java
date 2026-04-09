package com.booking.domain;

import java.time.LocalDateTime;

public class BlacklistEntry {
    private Long id;
    private Long userId;
    private String reason;
    private Long blacklistedBy;
    private Integer durationDays;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private Boolean active;
    private Long liftedBy;
    private LocalDateTime liftedAt;
    private String liftReason;
    private LocalDateTime createdAt;

    // Joined
    private String userName;
    private String blacklistedByName;
    private String liftedByName;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getBlacklistedBy() { return blacklistedBy; }
    public void setBlacklistedBy(Long blacklistedBy) { this.blacklistedBy = blacklistedBy; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Long getLiftedBy() { return liftedBy; }
    public void setLiftedBy(Long liftedBy) { this.liftedBy = liftedBy; }
    public LocalDateTime getLiftedAt() { return liftedAt; }
    public void setLiftedAt(LocalDateTime liftedAt) { this.liftedAt = liftedAt; }
    public String getLiftReason() { return liftReason; }
    public void setLiftReason(String liftReason) { this.liftReason = liftReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getBlacklistedByName() { return blacklistedByName; }
    public void setBlacklistedByName(String blacklistedByName) { this.blacklistedByName = blacklistedByName; }
    public String getLiftedByName() { return liftedByName; }
    public void setLiftedByName(String liftedByName) { this.liftedByName = liftedByName; }
}
