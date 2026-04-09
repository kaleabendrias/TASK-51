package com.booking.unit;

import com.booking.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
        "CREATED,CONFIRMED,true",      "CREATED,CANCELLED,true",
        "CREATED,PAID,false",           "CREATED,COMPLETED,false",
        "CONFIRMED,PAID,true",          "CONFIRMED,CANCELLED,true",
        "CONFIRMED,CHECKED_IN,false",   "CONFIRMED,CREATED,false",
        "PAID,CHECKED_IN,true",         "PAID,CANCELLED,true",
        "PAID,REFUNDED,true",           "PAID,COMPLETED,false",
        "CHECKED_IN,CHECKED_OUT,true",  "CHECKED_IN,CANCELLED,true",
        "CHECKED_IN,COMPLETED,false",
        "CHECKED_OUT,COMPLETED,true",   "CHECKED_OUT,CANCELLED,false",
        "COMPLETED,REFUNDED,true",      "COMPLETED,CANCELLED,false",
        "CANCELLED,CONFIRMED,false",    "CANCELLED,REFUNDED,false",
        "REFUNDED,CREATED,false",       "REFUNDED,COMPLETED,false"
    })
    void testTransitions(String from, String to, boolean expected) {
        assertEquals(expected, OrderStatus.valueOf(from).canTransitionTo(OrderStatus.valueOf(to)));
    }

    @Test
    void testTerminalStates() {
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertTrue(OrderStatus.REFUNDED.isTerminal());
        assertTrue(OrderStatus.COMPLETED.isTerminal());
        assertFalse(OrderStatus.CREATED.isTerminal());
        assertFalse(OrderStatus.PAID.isTerminal());
    }

    @Test
    void testRefundable() {
        assertTrue(OrderStatus.PAID.isRefundable());
        assertTrue(OrderStatus.CHECKED_IN.isRefundable());
        assertTrue(OrderStatus.COMPLETED.isRefundable());
        assertFalse(OrderStatus.CREATED.isRefundable());
        assertFalse(OrderStatus.CONFIRMED.isRefundable());
        assertFalse(OrderStatus.CANCELLED.isRefundable());
    }

    @Test
    void testCanReschedule() {
        assertTrue(OrderStatus.CREATED.canReschedule());
        assertTrue(OrderStatus.CONFIRMED.canReschedule());
        assertTrue(OrderStatus.PAID.canReschedule());
        assertFalse(OrderStatus.CHECKED_IN.canReschedule());
        assertFalse(OrderStatus.COMPLETED.canReschedule());
        assertFalse(OrderStatus.CANCELLED.canReschedule());
    }
}
