package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Order workflow integration tests — fully independent and self-contained.
 *
 * Every test creates its own time slot with UUID-suffixed idempotency keys,
 * so tests are safe to run in any JVM order without shared state.
 *
 * Tests 1-3 use seeded slots with capacity ≥ 2 so that parallel or out-of-order
 * execution by Maven Surefire does not cause spurious "fully booked" failures.
 * The oversell test always creates a fresh capacity-1 slot.
 */
class OrderWorkflowApiIT extends BaseApiIT {

    /** Creates a fresh time slot owned by photo1 and returns its ID. */
    private long freshSlot(MockHttpSession photo, int listingId, String date) throws Exception {
        MvcResult r = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "listingId", listingId,
                        "slotDate",  date,
                        "startTime", "09:00",
                        "endTime",   "11:00",
                        "capacity",  5))))
            .andExpect(status().isOk())
            .andReturn();
        return ((Number) parseMap(r).get("id")).longValue();
    }

    @Test
    void createOrderHappyPath() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-08-01");

        mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "wf-create-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.orderNumber").isNotEmpty())
            .andExpect(jsonPath("$.paymentDeadline").isNotEmpty())
            .andExpect(jsonPath("$.totalPrice").isNumber());
    }

    @Test
    void idempotencyReturnsCachedResponse() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-08-02");
        String idem = "wf-idem-" + UUID.randomUUID();

        // First call
        MvcResult first = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem)
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();

        // Retry with same key — must return the same order ID
        MvcResult second = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem)
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();

        assertEquals(parseMap(first).get("id"), parseMap(second).get("id"),
                "Same idempotency key must return the same cached order ID");
    }

    @Test
    void oversellPrevention() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession cust2 = loginAs("cust2");
        MockHttpSession photo  = loginAs("photo1");

        // Create a dedicated capacity-1 slot for this test
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "listingId", 1, "slotDate", "2026-08-03",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        long slotId = ((Number) parseMap(slotR).get("id")).longValue();

        // First booking fills the capacity-1 slot
        mvc.perform(post("/api/orders").session(cust1)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "wf-oversell-1st-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Second booking on the same slot must be rejected
        mvc.perform(post("/api/orders").session(cust2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "wf-oversell-2nd-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("fully booked")));
    }

    @Test
    void fullLifecycleConfirmPayCheckInOutComplete() throws Exception {
        MockHttpSession cust  = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-08-04");

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "wf-lc-create-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();
        String pfx = "wf-lc-" + orderId;

        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", pfx + "-confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", pfx + "-pay")
                .content(json(Map.of("amount", 150.0, "paymentReference", "REF-WF"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));

        mvc.perform(post("/api/orders/" + orderId + "/check-in").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", pfx + "-checkin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        mvc.perform(post("/api/orders/" + orderId + "/check-out").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", pfx + "-checkout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_OUT"));

        mvc.perform(post("/api/orders/" + orderId + "/complete").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", pfx + "-complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/api/orders/" + orderId + "/audit").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(6)));
    }

    @Test
    void cancelBranchAutoRefundsIfPaid() throws Exception {
        MockHttpSession cust  = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 3, "2026-08-05");

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "wf-cancel-create-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 3, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();
        String pfx = "wf-cancel-" + orderId;

        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", pfx + "-confirm"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", pfx + "-pay")
                .content(json(Map.of("amount", 300.0, "paymentReference", "REF-C"))))
            .andExpect(status().isOk());

        mvc.perform(post("/api/orders/" + orderId + "/cancel").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", pfx + "-cancel")
                .content(json(Map.of("reason", "Changed plans"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.refundAmount").value(300.0));
    }

    @Test
    void invalidTransitionBlocked() throws Exception {
        MockHttpSession cust  = loginAs("cust1");
        MockHttpSession photo = loginAs("photo2");
        long slotId = freshSlot(photo, 2, "2026-08-06");

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "wf-invalid-create-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 2, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        mvc.perform(post("/api/orders/" + orderId + "/complete").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "wf-invalid-complete-" + UUID.randomUUID()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Invalid transition")));
    }

    @Test
    void customerCannotConfirm() throws Exception {
        MockHttpSession cust  = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-08-07");

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "wf-rbac-create-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "wf-rbac-confirm-" + UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void otherPhotographerCannotConfirm() throws Exception {
        MockHttpSession cust   = loginAs("cust1");
        MockHttpSession photo1 = loginAs("photo1");
        MockHttpSession photo2 = loginAs("photo2");

        long slotId = freshSlot(photo1, 3, "2026-08-08");

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "wf-wrong-photo-create-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 3, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo2)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "wf-wrong-photo-confirm-" + UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }
}
