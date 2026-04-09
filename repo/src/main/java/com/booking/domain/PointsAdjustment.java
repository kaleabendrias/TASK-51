package com.booking.domain;

import java.time.LocalDateTime;

public class PointsAdjustment {
    private Long id;
    private Long userId;
    private Long adjustedBy;
    private Integer points;
    private String reason;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private LocalDateTime createdAt;

    // Joined
    private String userName;
    private String adjustedByName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAdjustedBy() { return adjustedBy; }
    public void setAdjustedBy(Long adjustedBy) { this.adjustedBy = adjustedBy; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getBalanceBefore() { return balanceBefore; }
    public void setBalanceBefore(Integer balanceBefore) { this.balanceBefore = balanceBefore; }
    public Integer getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Integer balanceAfter) { this.balanceAfter = balanceAfter; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAdjustedByName() { return adjustedByName; }
    public void setAdjustedByName(String adjustedByName) { this.adjustedByName = adjustedByName; }
}
