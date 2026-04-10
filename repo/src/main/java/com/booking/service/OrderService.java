package com.booking.service;

import com.booking.domain.*;
import com.booking.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int PAYMENT_DEADLINE_MINUTES = 30;
    private static final Set<String> VALID_DELIVERY_MODES = Set.of("ONSITE", "PICKUP", "COURIER");

    private final OrderMapper orderMapper;
    private final OrderActionMapper orderActionMapper;
    private final ListingMapper listingMapper;
    private final TimeSlotService timeSlotService;
    private final NotificationService notificationService;
    private final PointsService pointsService;
    private final com.booking.mapper.PointsRuleMapper pointsRuleMapper;
    private final com.booking.mapper.AddressMapper addressMapper;

    public OrderService(OrderMapper orderMapper, OrderActionMapper orderActionMapper,
                        ListingMapper listingMapper, TimeSlotService timeSlotService,
                        NotificationService notificationService, PointsService pointsService,
                        com.booking.mapper.PointsRuleMapper pointsRuleMapper,
                        com.booking.mapper.AddressMapper addressMapper) {
        this.orderMapper = orderMapper;
        this.orderActionMapper = orderActionMapper;
        this.listingMapper = listingMapper;
        this.timeSlotService = timeSlotService;
        this.notificationService = notificationService;
        this.pointsService = pointsService;
        this.pointsRuleMapper = pointsRuleMapper;
        this.addressMapper = addressMapper;
    }

    public Order getById(Long id) {
        return orderMapper.findById(id);
    }

    public Order getByOrderNumber(String orderNumber) {
        return orderMapper.findByOrderNumber(orderNumber);
    }

    public List<Order> getForUser(User user) {
        return switch (user.getRoleName()) {
            case "CUSTOMER" -> orderMapper.findByCustomerId(user.getId());
            case "PHOTOGRAPHER", "SERVICE_PROVIDER" -> orderMapper.findByPhotographerId(user.getId());
            default -> orderMapper.findAll();
        };
    }

    public List<OrderAction> getAuditTrail(Long orderId) {
        return orderActionMapper.findByOrderId(orderId);
    }

    public boolean canUserAccess(Order order, User user) {
        if ("ADMINISTRATOR".equals(user.getRoleName())) return true;
        if (order.getCustomerId().equals(user.getId())) return true;
        if (order.getPhotographerId().equals(user.getId())) return true;
        return false;
    }

    @Transactional
    public Order createOrder(Long listingId, Long timeSlotId, Long addressId,
                             String notes, String deliveryMode, User customer) {
        // Validate delivery mode
        String mode = (deliveryMode != null) ? deliveryMode.toUpperCase() : "ONSITE";
        if (!VALID_DELIVERY_MODES.contains(mode)) {
            throw new IllegalArgumentException("Invalid delivery mode. Must be one of: " + VALID_DELIVERY_MODES);
        }
        if ("COURIER".equals(mode) && addressId == null) {
            throw new IllegalArgumentException("Address is required for courier delivery");
        }
        // Verify address ownership for courier orders
        if (addressId != null) {
            com.booking.domain.Address addr = addressMapper.findById(addressId);
            if (addr == null) {
                throw new IllegalArgumentException("Address not found");
            }
            if (!addr.getUserId().equals(customer.getId())) {
                throw new SecurityException("Address does not belong to the current user");
            }
        }
        Listing listing = listingMapper.findById(listingId);
        if (listing == null || !listing.getActive()) {
            throw new IllegalArgumentException("Listing not found or inactive");
        }

        // Reserve time slot with row lock — prevents oversell
        TimeSlot slot;
        try {
            slot = timeSlotService.reserveSlot(timeSlotId);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Cannot reserve time slot: " + e.getMessage());
        }

        // Verify slot belongs to listing
        if (!slot.getListingId().equals(listingId)) {
            // Compensating rollback
            timeSlotService.releaseSlot(timeSlotId);
            throw new IllegalArgumentException("Time slot does not belong to this listing");
        }

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setCustomerId(customer.getId());
        order.setPhotographerId(listing.getPhotographerId());
        order.setListingId(listingId);
        order.setTimeSlotId(timeSlotId);
        order.setStatus(OrderStatus.CREATED.name());
        order.setTotalPrice(listing.getPrice());
        order.setPaidAmount(BigDecimal.ZERO);
        order.setAddressId(addressId);
        order.setNotes(notes);
        order.setDeliveryMode(mode);
        // Set explicit ETA fields based on delivery mode
        if ("COURIER".equals(mode)) {
            order.setDeliveryEta(slot.getSlotDate().atTime(slot.getEndTime()).plusHours(2));
        } else if ("PICKUP".equals(mode)) {
            order.setPickupEta(slot.getSlotDate().atTime(slot.getEndTime()).plusMinutes(30));
        }
        order.setPaymentDeadline(LocalDateTime.now().plusMinutes(PAYMENT_DEADLINE_MINUTES));

        try {
            orderMapper.insert(order);
        } catch (Exception e) {
            // Compensating rollback: release the reserved slot
            timeSlotService.releaseSlot(timeSlotId);
            throw new RuntimeException("Order creation failed, slot released", e);
        }

        recordAction(order.getId(), "CREATE", null, OrderStatus.CREATED.name(),
                customer.getId(), "Order created, payment due in " + PAYMENT_DEADLINE_MINUTES + " min");

        notificationService.queueOrderNotification(order, "ORDER_CREATED",
                "New order " + order.getOrderNumber() + " created");

        return orderMapper.findById(order.getId());
    }

    @Transactional
    public Order confirm(Long orderId, User actor) {
        Order order = requireOrder(orderId);
        enforceAccess(order, actor, "PHOTOGRAPHER", "SERVICE_PROVIDER", "ADMINISTRATOR");
        transition(order, OrderStatus.CONFIRMED, actor.getId(), "Order confirmed by photographer");
        notificationService.queueOrderNotification(order, "ORDER_CONFIRMED",
                "Order " + order.getOrderNumber() + " has been confirmed");
        // HOLD notification: customer must pay within deadline
        notificationService.queueHoldNotification(order.getCustomerId(),
                order.getOrderNumber(), "Awaiting payment — deadline: " + order.getPaymentDeadline());
        return orderMapper.findById(orderId);
    }

    @Transactional
    public Order recordPayment(Long orderId, BigDecimal amount, String paymentRef, User actor) {
        Order order = requireOrder(orderId);
        enforceAccess(order, actor, "CUSTOMER", "ADMINISTRATOR");
        transition(order, OrderStatus.PAID, actor.getId(),
                "Payment of " + amount + " recorded, ref: " + paymentRef);
        order.setPaidAmount(amount);
        order.setPaymentReference(paymentRef);
        order.setStatus(OrderStatus.PAID.name());
        orderMapper.update(order);

        // Award points for payment — driven by configurable rules
        awardByRule("ORDER_PAID", order);

        notificationService.queueOrderNotification(order, "PAYMENT_RECEIVED",
                "Payment received for order " + order.getOrderNumber());
        return orderMapper.findById(orderId);
    }

    @Transactional
    public Order checkIn(Long orderId, User actor) {
        Order order = requireOrder(orderId);
        enforceAccess(order, actor, "PHOTOGRAPHER", "SERVICE_PROVIDER", "ADMINISTRATOR");
        transition(order, OrderStatus.CHECKED_IN, actor.getId(), "Customer checked in");
        return orderMapper.findById(orderId);
    }

    @Transactional
    public Order checkOut(Long orderId, User actor) {
        Order order = requireOrder(orderId);
        enforceAccess(order, actor, "PHOTOGRAPHER", "SERVICE_PROVIDER", "ADMINISTRATOR");
        transition(order, OrderStatus.CHECKED_OUT, actor.getId(), "Customer checked out");
        return orderMapper.findById(orderId);
    }

    @Transactional
    public Order complete(Long orderId, User actor) {
        Order order = requireOrder(orderId);
        enforceAccess(order, actor, "PHOTOGRAPHER", "SERVICE_PROVIDER", "ADMINISTRATOR");
        transition(order, OrderStatus.COMPLETED, actor.getId(), "Order completed");

        // Award completion bonus — driven by configurable rules
        awardByRule("ORDER_COMPLETED", order);

        notificationService.queueOrderNotification(order, "ORDER_COMPLETED",
                "Order " + order.getOrderNumber() + " is now complete");
        return orderMapper.findById(orderId);
    }

    @Transactional
    public Order cancel(Long orderId, String reason, User actor) {
        Order order = requireOrder(orderId);
        enforceAccess(order, actor, "CUSTOMER", "PHOTOGRAPHER", "SERVICE_PROVIDER", "ADMINISTRATOR");

        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new IllegalStateException("Cannot cancel from status " + current);
        }

        transition(order, OrderStatus.CANCELLED, actor.getId(), "Cancelled: " + reason);
        order.setCancelReason(reason);
        order.setStatus(OrderStatus.CANCELLED.name());
        orderMapper.update(order);

        // Compensating rollback: release the time slot
        timeSlotService.releaseSlot(order.getTimeSlotId());

        // If already paid, auto-refund
        if (current == OrderStatus.PAID || current == OrderStatus.CHECKED_IN) {
            order.setRefundAmount(order.getPaidAmount());
            orderMapper.update(order);
            recordAction(orderId, "AUTO_REFUND", OrderStatus.CANCELLED.name(),
                    OrderStatus.CANCELLED.name(), actor.getId(),
                    "Auto-refund of " + order.getPaidAmount());
        }

        notificationService.queueOrderNotification(order, "ORDER_CANCELLED",
                "Order " + order.getOrderNumber() + " has been cancelled");
        return orderMapper.findById(orderId);
    }

    @Transactional
    public Order refund(Long orderId, BigDecimal amount, String reason, User actor) {
        Order order = requireOrder(orderId);
        enforceAccess(order, actor, "ADMINISTRATOR");

        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.isRefundable() && current != OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is not refundable in status " + current);
        }

        if (current != OrderStatus.CANCELLED) {
            transition(order, OrderStatus.REFUNDED, actor.getId(),
                    "Refund of " + amount + ": " + reason);
        }
        // APPROVAL notification: inform customer of refund approval
        notificationService.queueApprovalNotification(order.getCustomerId(),
                order.getOrderNumber(), "Refund of $" + amount + " approved");
        order.setRefundAmount(amount);
        order.setStatus(order.getStatus().equals(OrderStatus.CANCELLED.name()) ?
                OrderStatus.CANCELLED.name() : OrderStatus.REFUNDED.name());
        orderMapper.update(order);

        // Release slot if not already released
        if (current != OrderStatus.CANCELLED) {
            timeSlotService.releaseSlot(order.getTimeSlotId());
        }

        // Deduct points — lookup the payment rule value for consistent reversal
        com.booking.domain.PointsRule payRule = pointsRuleMapper.findByTrigger("ORDER_PAID");
        int deductAmt = (payRule != null) ? payRule.getPoints() : 10;
        pointsService.deductPoints(order.getCustomerId(), deductAmt,
                "REFUND_DEDUCTION", "ORDER", order.getId(),
                "Points reversed for refunded order " + order.getOrderNumber());

        notificationService.queueOrderNotification(order, "ORDER_REFUNDED",
                "Refund of $" + amount + " processed for order " + order.getOrderNumber());
        return orderMapper.findById(orderId);
    }

    @Transactional
    public Order reschedule(Long orderId, Long newTimeSlotId, User actor) {
        Order order = requireOrder(orderId);
        enforceAccess(order, actor, "CUSTOMER", "ADMINISTRATOR");

        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.canReschedule()) {
            throw new IllegalStateException("Cannot reschedule from status " + current);
        }

        Long oldSlotId = order.getTimeSlotId();

        // Verify new slot belongs to the same listing as the order
        com.booking.domain.TimeSlot newSlotCheck = timeSlotService.getById(newTimeSlotId);
        if (newSlotCheck == null) {
            throw new IllegalArgumentException("Time slot not found");
        }
        if (!newSlotCheck.getListingId().equals(order.getListingId())) {
            throw new IllegalArgumentException("Cannot reschedule to a slot from a different listing");
        }

        // Reserve new slot first
        try {
            timeSlotService.reserveSlot(newTimeSlotId);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("New time slot unavailable: " + e.getMessage());
        }

        // Release old slot
        try {
            timeSlotService.releaseSlot(oldSlotId);
        } catch (Exception e) {
            // Compensating rollback: release the newly reserved slot
            timeSlotService.releaseSlot(newTimeSlotId);
            throw new RuntimeException("Reschedule failed during old slot release", e);
        }

        order.setTimeSlotId(newTimeSlotId);
        orderMapper.update(order);

        recordAction(orderId, "RESCHEDULE", current.name(), current.name(),
                actor.getId(), "Rescheduled from slot " + oldSlotId + " to " + newTimeSlotId);

        notificationService.queueOrderNotification(order, "ORDER_RESCHEDULED",
                "Order " + order.getOrderNumber() + " has been rescheduled");
        return orderMapper.findById(orderId);
    }

    @Transactional
    public void autoCloseUnpaidOrders() {
        List<Order> expired = orderMapper.findUnpaidExpired(LocalDateTime.now());
        for (Order order : expired) {
            log.info("Auto-closing unpaid order {}", order.getOrderNumber());
            transition(order, OrderStatus.CANCELLED, 1L, "Auto-cancelled: payment deadline expired");
            order.setCancelReason("Payment deadline expired");
            order.setStatus(OrderStatus.CANCELLED.name());
            orderMapper.update(order);
            timeSlotService.releaseSlot(order.getTimeSlotId());
            notificationService.queueOrderNotification(order, "ORDER_AUTO_CANCELLED",
                    "Order " + order.getOrderNumber() + " cancelled due to payment timeout");
        }
    }

    /**
     * Sends overdue warnings for orders approaching payment deadline (within 10 minutes).
     */
    public void sendOverdueWarnings() {
        LocalDateTime warningThreshold = LocalDateTime.now().plusMinutes(10);
        List<Order> approaching = orderMapper.findUnpaidExpired(warningThreshold);
        for (Order order : approaching) {
            if ("CREATED".equals(order.getStatus())) {
                notificationService.queueOverdueNotification(
                        order.getCustomerId(), order.getOrderNumber());
            }
        }
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        return order;
    }

    private void enforceAccess(Order order, User actor, String... allowedRoles) {
        if ("ADMINISTRATOR".equals(actor.getRoleName())) return;
        for (String role : allowedRoles) {
            if (role.equals(actor.getRoleName())) {
                if ("CUSTOMER".equals(role) && order.getCustomerId().equals(actor.getId())) return;
                if (("PHOTOGRAPHER".equals(role) || "SERVICE_PROVIDER".equals(role))
                        && order.getPhotographerId().equals(actor.getId())) return;
            }
        }
        throw new SecurityException("Access denied to this order");
    }

    private void transition(Order order, OrderStatus target, Long actorId, String detail) {
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid transition: " + current + " -> " + target);
        }
        orderMapper.updateStatus(order.getId(), target.name());
        recordAction(order.getId(), target.name(), current.name(), target.name(), actorId, detail);
    }

    private void awardByRule(String triggerEvent, Order order) {
        pointsService.awardByTrigger(triggerEvent, order.getCustomerId(),
                "ORDER", order.getId(), "for order " + order.getOrderNumber());
    }

    private void recordAction(Long orderId, String action, String from, String to,
                              Long performedBy, String detail) {
        OrderAction oa = new OrderAction();
        oa.setOrderId(orderId);
        oa.setAction(action);
        oa.setFromStatus(from);
        oa.setToStatus(to);
        oa.setPerformedBy(performedBy);
        oa.setDetail(detail);
        orderActionMapper.insert(oa);
    }
}
