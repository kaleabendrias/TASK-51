package com.booking.domain;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    CREATED,
    CONFIRMED,
    PAID,
    CHECKED_IN,
    CHECKED_OUT,
    COMPLETED,
    CANCELLED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            CREATED,     Set.of(CONFIRMED, CANCELLED),
            CONFIRMED,   Set.of(PAID, CANCELLED),
            PAID,        Set.of(CHECKED_IN, CANCELLED, REFUNDED),
            CHECKED_IN,  Set.of(CHECKED_OUT, CANCELLED),
            CHECKED_OUT, Set.of(COMPLETED),
            COMPLETED,   Set.of(REFUNDED),
            CANCELLED,   Set.of(),
            REFUNDED,    Set.of()
    );

    public boolean canTransitionTo(OrderStatus target) {
        Set<OrderStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == REFUNDED || this == COMPLETED;
    }

    public boolean isRefundable() {
        return this == PAID || this == CHECKED_IN || this == COMPLETED;
    }

    public boolean canReschedule() {
        return this == CREATED || this == CONFIRMED || this == PAID;
    }
}
