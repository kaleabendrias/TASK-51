package com.booking.domain;

import java.time.LocalDateTime;

public class Conversation {
    private Long id;
    private Long participantOne;
    private Long participantTwo;
    private Long orderId;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;

    // Joined
    private String participantOneName;
    private String participantTwoName;
    private int unreadCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParticipantOne() { return participantOne; }
    public void setParticipantOne(Long participantOne) { this.participantOne = participantOne; }
    public Long getParticipantTwo() { return participantTwo; }
    public void setParticipantTwo(Long participantTwo) { this.participantTwo = participantTwo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getParticipantOneName() { return participantOneName; }
    public void setParticipantOneName(String participantOneName) { this.participantOneName = participantOneName; }
    public String getParticipantTwoName() { return participantTwoName; }
    public void setParticipantTwoName(String participantTwoName) { this.participantTwoName = participantTwoName; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}
