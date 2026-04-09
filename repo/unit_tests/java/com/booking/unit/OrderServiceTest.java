package com.booking.unit;

import com.booking.domain.*;
import com.booking.mapper.*;
import com.booking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderMapper orderMapper;
    @Mock OrderActionMapper orderActionMapper;
    @Mock ListingMapper listingMapper;
    @Mock TimeSlotService timeSlotService;
    @Mock NotificationService notificationService;
    @Mock PointsService pointsService;
    @Mock com.booking.mapper.PointsRuleMapper pointsRuleMapper;
    @InjectMocks OrderService orderService;

    private User customer, photographer, admin;
    private Listing listing;
    private TimeSlot slot;

    @BeforeEach
    void setup() {
        customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        photographer = new User(); photographer.setId(2L); photographer.setRoleName("PHOTOGRAPHER");
        admin = new User(); admin.setId(1L); admin.setRoleName("ADMINISTRATOR");
        listing = new Listing(); listing.setId(1L); listing.setPhotographerId(2L);
        listing.setActive(true); listing.setPrice(BigDecimal.valueOf(150));
        slot = new TimeSlot(); slot.setId(1L); slot.setListingId(1L);
        slot.setCapacity(1); slot.setBookedCount(0); slot.setVersion(0);
    }

    private Order makeOrder(String status) {
        Order o = new Order(); o.setId(1L); o.setOrderNumber("ORD-TEST");
        o.setCustomerId(4L); o.setPhotographerId(2L); o.setListingId(1L);
        o.setTimeSlotId(1L); o.setStatus(status); o.setTotalPrice(BigDecimal.valueOf(150));
        o.setPaidAmount(BigDecimal.ZERO);
        return o;
    }

    @Test
    void createOrderSuccess() {
        when(listingMapper.findById(1L)).thenReturn(listing);
        when(timeSlotService.reserveSlot(1L)).thenReturn(slot);
        when(orderMapper.findById(any())).thenReturn(makeOrder("CREATED"));

        Order result = orderService.createOrder(1L, 1L, null, "notes", null, customer);
        assertNotNull(result);
        verify(timeSlotService).reserveSlot(1L);
        verify(orderMapper).insert(any());
        verify(orderActionMapper).insert(any());
        verify(notificationService).queueOrderNotification(any(), eq("ORDER_CREATED"), anyString());
    }

    @Test
    void createOrderInactiveListingFails() {
        listing.setActive(false);
        when(listingMapper.findById(1L)).thenReturn(listing);
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(1L, 1L, null, null, null, customer));
    }

    @Test
    void createOrderSlotFullTriggersCompensation() {
        when(listingMapper.findById(1L)).thenReturn(listing);
        when(timeSlotService.reserveSlot(1L)).thenThrow(new IllegalStateException("Full"));
        assertThrows(IllegalStateException.class, () -> orderService.createOrder(1L, 1L, null, null, null, customer));
    }

    @Test
    void createOrderSlotWrongListingTriggersCompensation() {
        slot.setListingId(99L); // wrong listing
        when(listingMapper.findById(1L)).thenReturn(listing);
        when(timeSlotService.reserveSlot(1L)).thenReturn(slot);
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(1L, 1L, null, null, null, customer));
        verify(timeSlotService).releaseSlot(1L); // compensation
    }

    @Test
    void confirmByPhotographer() {
        Order o = makeOrder("CREATED");
        when(orderMapper.findById(1L)).thenReturn(o);
        when(orderMapper.findById(1L)).thenReturn(o).thenReturn(makeOrder("CONFIRMED"));
        Order result = orderService.confirm(1L, photographer);
        verify(orderMapper).updateStatus(1L, "CONFIRMED");
    }

    @Test
    void confirmByCustomerDenied() {
        when(orderMapper.findById(1L)).thenReturn(makeOrder("CREATED"));
        assertThrows(SecurityException.class, () -> orderService.confirm(1L, customer));
    }

    @Test
    void recordPaymentDelegatesPointsToMultiScopeEngine() {
        Order o = makeOrder("CONFIRMED");
        when(orderMapper.findById(1L)).thenReturn(o).thenReturn(makeOrder("PAID"));
        orderService.recordPayment(1L, BigDecimal.valueOf(150), "REF1", customer);
        verify(pointsService).awardByTrigger(eq("ORDER_PAID"), eq(4L), eq("ORDER"), any(), anyString());
    }

    @Test
    void completeAwardsDelegatesPointsToMultiScopeEngine() {
        Order o = makeOrder("CHECKED_OUT");
        when(orderMapper.findById(1L)).thenReturn(o).thenReturn(makeOrder("COMPLETED"));
        orderService.complete(1L, photographer);
        verify(pointsService).awardByTrigger(eq("ORDER_COMPLETED"), eq(4L), eq("ORDER"), any(), anyString());
    }

    @Test
    void cancelReleasesSlotAndAutoRefundsIfPaid() {
        Order o = makeOrder("PAID"); o.setPaidAmount(BigDecimal.valueOf(150));
        when(orderMapper.findById(1L)).thenReturn(o).thenReturn(o);
        orderService.cancel(1L, "Changed mind", customer);
        verify(timeSlotService).releaseSlot(1L);
        verify(orderMapper, atLeast(1)).update(argThat(order -> order.getRefundAmount() != null));
    }

    @Test
    void cancelFromCreatedNoRefund() {
        Order o = makeOrder("CREATED");
        when(orderMapper.findById(1L)).thenReturn(o).thenReturn(o);
        orderService.cancel(1L, "Nope", customer);
        verify(timeSlotService).releaseSlot(1L);
        // No refund action since not paid
        verify(orderActionMapper, times(1)).insert(any());
    }

    @Test
    void invalidTransitionThrows() {
        Order o = makeOrder("COMPLETED");
        when(orderMapper.findById(1L)).thenReturn(o);
        assertThrows(IllegalStateException.class, () -> orderService.confirm(1L, photographer));
    }

    @Test
    void refundDeductsPointsUsingRulesEngine() {
        com.booking.domain.PointsRule payRule = new com.booking.domain.PointsRule();
        payRule.setPoints(10); payRule.setActive(true);
        when(pointsRuleMapper.findByTrigger("ORDER_PAID")).thenReturn(payRule);

        Order o = makeOrder("COMPLETED"); o.setPaidAmount(BigDecimal.valueOf(150));
        when(orderMapper.findById(1L)).thenReturn(o).thenReturn(o);
        orderService.refund(1L, BigDecimal.valueOf(150), "Unsatisfied", admin);
        verify(pointsService).deductPoints(eq(4L), eq(10), eq("REFUND_DEDUCTION"), any(), any(), any());
        verify(timeSlotService).releaseSlot(1L);
    }

    @Test
    void rescheduleReservesNewReleasesOld() {
        Order o = makeOrder("CREATED");
        TimeSlot newSlot = new TimeSlot(); newSlot.setId(5L); newSlot.setListingId(1L);
        when(orderMapper.findById(1L)).thenReturn(o).thenReturn(o);
        when(timeSlotService.reserveSlot(5L)).thenReturn(newSlot);
        orderService.reschedule(1L, 5L, customer);
        verify(timeSlotService).reserveSlot(5L);
        verify(timeSlotService).releaseSlot(1L);
    }

    @Test
    void rescheduleFromTerminalDenied() {
        Order o = makeOrder("COMPLETED");
        when(orderMapper.findById(1L)).thenReturn(o);
        assertThrows(IllegalStateException.class, () -> orderService.reschedule(1L, 5L, customer));
    }

    @Test
    void autoCloseUnpaidOrders() {
        Order expired = makeOrder("CREATED");
        when(orderMapper.findUnpaidExpired(any())).thenReturn(List.of(expired));
        orderService.autoCloseUnpaidOrders();
        verify(orderMapper).updateStatus(1L, "CANCELLED");
        verify(timeSlotService).releaseSlot(1L);
    }

    @Test
    void canUserAccessAdmin() { assertTrue(orderService.canUserAccess(makeOrder("CREATED"), admin)); }
    @Test
    void canUserAccessCustomerOwner() { assertTrue(orderService.canUserAccess(makeOrder("CREATED"), customer)); }
    @Test
    void canUserAccessPhotographerOwner() { assertTrue(orderService.canUserAccess(makeOrder("CREATED"), photographer)); }
    @Test
    void canUserAccessDenied() {
        User other = new User(); other.setId(99L); other.setRoleName("CUSTOMER");
        assertFalse(orderService.canUserAccess(makeOrder("CREATED"), other));
    }

    @Test
    void getForUserRoutesByRole() {
        when(orderMapper.findByCustomerId(4L)).thenReturn(Collections.emptyList());
        when(orderMapper.findByPhotographerId(2L)).thenReturn(Collections.emptyList());
        when(orderMapper.findAll()).thenReturn(Collections.emptyList());
        orderService.getForUser(customer);  verify(orderMapper).findByCustomerId(4L);
        orderService.getForUser(photographer); verify(orderMapper).findByPhotographerId(2L);
        orderService.getForUser(admin); verify(orderMapper).findAll();
    }
}
