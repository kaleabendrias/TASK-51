package com.booking.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {
    private Long id;
    private String orderNumber;
    private Long customerId;
    private Long photographerId;
    private Long listingId;
    private Long timeSlotId;
    private String status;
    private BigDecimal totalPrice;
    private BigDecimal paidAmount;
    private String paymentReference;
    private String cancelReason;
    private BigDecimal refundAmount;
    private Long rescheduleFromId;
    private Long addressId;
    private String notes;
    private String deliveryMode;
    private LocalDateTime deliveryEta;
    private LocalDateTime pickupEta;
    private LocalDateTime paymentDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Joined
    private String customerName;
    private String photographerName;
    private String listingTitle;
    private String slotDate;
    private String slotTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getPhotographerId() { return photographerId; }
    public void setPhotographerId(Long photographerId) { this.photographerId = photographerId; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long listingId) { this.listingId = listingId; }
    public Long getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(Long timeSlotId) { this.timeSlotId = timeSlotId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    @JsonIgnore
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public Long getRescheduleFromId() { return rescheduleFromId; }
    public void setRescheduleFromId(Long rescheduleFromId) { this.rescheduleFromId = rescheduleFromId; }
    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }
    public LocalDateTime getDeliveryEta() { return deliveryEta; }
    public void setDeliveryEta(LocalDateTime deliveryEta) { this.deliveryEta = deliveryEta; }
    public LocalDateTime getPickupEta() { return pickupEta; }
    public void setPickupEta(LocalDateTime pickupEta) { this.pickupEta = pickupEta; }
    public LocalDateTime getPaymentDeadline() { return paymentDeadline; }
    public void setPaymentDeadline(LocalDateTime paymentDeadline) { this.paymentDeadline = paymentDeadline; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getPhotographerName() { return photographerName; }
    public void setPhotographerName(String photographerName) { this.photographerName = photographerName; }
    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }
    public String getSlotDate() { return slotDate; }
    public void setSlotDate(String slotDate) { this.slotDate = slotDate; }
    public String getSlotTime() { return slotTime; }
    public void setSlotTime(String slotTime) { this.slotTime = slotTime; }
}
