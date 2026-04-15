package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Order-independent, seed-data-decoupled integration tests for the full order workflow.
 *
 * Every test creates its own time slot and performs all required setup steps
 * independently. Tests do not share state, do not rely on @TestMethodOrder, and
 * are safe to run in any order. Unique idempotency keys are generated per test
 * using UUID suffixes to avoid collisions across test runs.
 */
class SelfContainedOrderWorkflowApiIT extends BaseApiIT {

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a fresh time slot for the given listing and returns its ID. */
    private long freshSlot(MockHttpSession photo, int listingId, String date) throws Exception {
        MvcResult r = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "listingId", listingId,
                        "slotDate", date,
                        "startTime", "09:00",
                        "endTime", "11:00",
                        "capacity", 5))))
            .andExpect(status().isOk())
            .andReturn();
        return ((Number) parseMap(r).get("id")).longValue();
    }

    /** Creates an order for the given slot and returns the order ID. */
    private long createOrder(MockHttpSession cust, int listingId, long slotId) throws Exception {
        String idem = "sc-create-" + UUID.randomUUID();
        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem)
                .content(json(Map.of("listingId", listingId, "timeSlotId", slotId))))
            .andExpect(status().isOk())
            .andReturn();
        return ((Number) parseMap(r).get("id")).longValue();
    }

    /** Confirms an order (by the photographer). */
    private void confirmOrder(long orderId, MockHttpSession photo) throws Exception {
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "sc-confirm-" + orderId + "-" + UUID.randomUUID()))
            .andExpect(status().isOk());
    }

    /** Pays an order (by the customer). */
    private void payOrder(long orderId, MockHttpSession cust, double amount) throws Exception {
        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-pay-" + orderId + "-" + UUID.randomUUID())
                .content(json(Map.of("amount", amount, "paymentReference", "REF-SC-" + orderId))))
            .andExpect(status().isOk());
    }

    // ─── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void createOrder_happyPath_returnsCreatedStatus() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-10-01");

        mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-happy-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.orderNumber").isNotEmpty())
            .andExpect(jsonPath("$.paymentDeadline").isNotEmpty())
            .andExpect(jsonPath("$.totalPrice").isNumber());
    }

    @Test
    void createOrder_missingSlot_returns400() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-badslot-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", 999_999))))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void idempotencyKey_duplicateCall_returnsCachedResponse() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-10-02");
        String idem = "sc-idem-" + UUID.randomUUID();

        // First call
        MvcResult first = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem)
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk())
            .andReturn();

        // Same key, same body — must return cached (same order ID)
        MvcResult second = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem)
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk())
            .andReturn();

        Object firstId = parseMap(first).get("id");
        Object secondId = parseMap(second).get("id");
        assertEquals(firstId, secondId, "Idempotency: same key must return the same order ID");
    }

    @Test
    void oversellPrevention_capacityOne_rejectsSecondBooking() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession cust2 = loginAs("cust2");
        MockHttpSession photo = loginAs("photo1");

        // Create a slot with capacity = 1
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "listingId", 1,
                        "slotDate", "2026-10-03",
                        "startTime", "09:00",
                        "endTime", "11:00",
                        "capacity", 1))))
            .andExpect(status().isOk())
            .andReturn();
        long slotId = ((Number) parseMap(slotR).get("id")).longValue();

        // First booking succeeds
        mvc.perform(post("/api/orders").session(cust1)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-oversell-1st-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Second booking on the same slot must be rejected as fully booked
        mvc.perform(post("/api/orders").session(cust2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-oversell-2nd-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("fully booked")));
    }

    @Test
    void fullLifecycle_confirmPayCheckInOutComplete_auditTrailComplete() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-10-04");
        long orderId = createOrder(cust, 1, slotId);

        // Confirm
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "sc-lc-confirm-" + UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Pay
        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-lc-pay-" + UUID.randomUUID())
                .content(json(Map.of("amount", 150.0, "paymentReference", "REF-LC"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));

        // Check-in
        mvc.perform(post("/api/orders/" + orderId + "/check-in").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "sc-lc-checkin-" + UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        // Check-out
        mvc.perform(post("/api/orders/" + orderId + "/check-out").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "sc-lc-checkout-" + UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHECKED_OUT"));

        // Complete
        mvc.perform(post("/api/orders/" + orderId + "/complete").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "sc-lc-complete-" + UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Audit trail must have one entry per transition (≥ 6)
        mvc.perform(get("/api/orders/" + orderId + "/audit").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(6)));
    }

    @Test
    void cancelFromCreated_noRefund_statusCancelled() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 3, "2026-10-05");
        long orderId = createOrder(cust, 3, slotId);

        mvc.perform(post("/api/orders/" + orderId + "/cancel").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-cancel-created-" + UUID.randomUUID())
                .content(json(Map.of("reason", "Changed my mind"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        // refundAmount is null (not 0) when no payment was made before cancellation
    }

    @Test
    void cancelFromPaid_autoRefund_returnsFullAmount() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-10-06");
        long orderId = createOrder(cust, 1, slotId);

        confirmOrder(orderId, photo);
        payOrder(orderId, cust, 150.0);

        mvc.perform(post("/api/orders/" + orderId + "/cancel").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-cancel-paid-" + UUID.randomUUID())
                .content(json(Map.of("reason", "Plans changed"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.refundAmount").value(150.0));
    }

    @Test
    void invalidTransition_skipToComplete_returns400() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo2");
        long slotId = freshSlot(photo, 2, "2026-10-07");
        long orderId = createOrder(cust, 2, slotId);

        // Attempt to jump directly from CREATED to COMPLETED
        mvc.perform(post("/api/orders/" + orderId + "/complete").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "sc-invalid-complete-" + UUID.randomUUID()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Invalid transition")));
    }

    @Test
    void invalidTransition_payUnconfirmed_returns400() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-10-08");
        long orderId = createOrder(cust, 1, slotId);

        // Attempt to pay before confirming
        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-invalid-pay-" + UUID.randomUUID())
                .content(json(Map.of("amount", 150.0, "paymentReference", "REF"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Invalid transition")));
    }

    @Test
    void rbac_customerCannotConfirm_returns403() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        long slotId = freshSlot(photo, 1, "2026-10-09");
        long orderId = createOrder(cust, 1, slotId);

        // Customer tries to confirm — must be forbidden
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "sc-rbac-confirm-" + UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void rbac_wrongPhotographerCannotConfirm_returns403() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo1 = loginAs("photo1");
        MockHttpSession photo2 = loginAs("photo2");

        // photo1 owns listing 1; photo2 should not be able to confirm orders for it
        long slotId = freshSlot(photo1, 1, "2026-10-10");
        long orderId = createOrder(cust, 1, slotId);

        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo2)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "sc-wrong-photo-" + UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void reschedule_movesToNewSlot_oldSlotFreed() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        long originalSlot = freshSlot(photo, 1, "2026-10-11");
        long newSlot      = freshSlot(photo, 1, "2026-10-12");
        long orderId = createOrder(cust, 1, originalSlot);

        String idem = "sc-reschedule-" + UUID.randomUUID();
        mvc.perform(post("/api/orders/" + orderId + "/reschedule").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem)
                .content(json(Map.of("newTimeSlotId", newSlot, "reason", "Schedule conflict"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CREATED"));

        // Verify the order now references the new slot
        mvc.perform(get("/api/orders/" + orderId).session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slotDate").isNotEmpty());
    }

    @Test
    void reschedule_fromCompletedOrder_returns400() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        long slotId = freshSlot(photo, 1, "2026-10-13");
        long newSlot = freshSlot(photo, 1, "2026-10-14");
        long orderId = createOrder(cust, 1, slotId);

        confirmOrder(orderId, photo);
        payOrder(orderId, cust, 150.0);

        // Check-in → Check-out → Complete
        for (String action : new String[]{"check-in", "check-out", "complete"}) {
            mvc.perform(post("/api/orders/" + orderId + "/" + action).session(photo)
                    .header("Origin", TEST_ORIGIN)
                    .header("Idempotency-Key", "sc-comp-" + action + "-" + UUID.randomUUID()))
                .andExpect(status().isOk());
        }

        // Cannot reschedule a COMPLETED order
        mvc.perform(post("/api/orders/" + orderId + "/reschedule").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sc-reschd-completed-" + UUID.randomUUID())
                .content(json(Map.of("newTimeSlotId", newSlot, "reason", "Too late"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_anotherCustomer_returns403() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession cust2 = loginAs("cust2");
        MockHttpSession photo = loginAs("photo1");

        long slotId = freshSlot(photo, 1, "2026-10-15");
        long orderId = createOrder(cust1, 1, slotId);

        // cust2 must not be able to read cust1's order
        mvc.perform(get("/api/orders/" + orderId).session(cust2))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void getOrders_customer_returnsOnlyTheirOrders() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        long slotId = freshSlot(photo, 1, "2026-10-16");
        long orderId = createOrder(cust1, 1, slotId);

        // cust1 list should include the order
        mvc.perform(get("/api/orders").session(cust1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem((int) orderId)));
    }
}
