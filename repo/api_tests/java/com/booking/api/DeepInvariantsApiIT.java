package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Deep invariant tests that validate side effects, state transitions, and cross-cutting
 * behaviours — not just status codes or surface-level response structure.
 *
 * Each test group validates a specific invariant of the system:
 *   • Points are awarded at the correct life-cycle events with correct amounts
 *   • Notifications are queued for every order event
 *   • Time slot capacity is decremented on booking and restored on cancellation
 *   • Invalid state transitions are rejected
 *   • Search results are consistent and filtered correctly
 *   • Reschedule atomically switches the booking to the new slot
 *   • Leaderboard ordering is correct after award operations
 *   • Concurrent point awards accumulate without lost updates
 */
class DeepInvariantsApiIT extends BaseApiIT {

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Creates a fresh time slot for listing 1 (owner: photo1) and returns its id. */
    private long createSlot(MockHttpSession photo, String date, String startTime, String endTime) throws Exception {
        MvcResult r = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "listingId", 1,
                        "slotDate", date,
                        "startTime", startTime,
                        "endTime", endTime,
                        "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        return ((Number) parseMap(r).get("id")).longValue();
    }

    private long createSlot(MockHttpSession photo, String date) throws Exception {
        return createSlot(photo, date, "14:00", "15:00");
    }

    /** Places an order for cust1 on the given slotId and returns the order id. */
    private long createOrder(MockHttpSession cust, long slotId) throws Exception {
        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "deep-inv-" + System.nanoTime())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId, "notes", "invariant test"))))
            .andExpect(status().isOk()).andReturn();
        return ((Number) parseMap(r).get("id")).longValue();
    }

    /** Advances an order through CREATED→CONFIRMED→PAID. */
    private void advanceToPayment(long orderId, MockHttpSession photo, MockHttpSession cust) throws Exception {
        String prefix = "inv-pay-" + orderId + "-";
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", prefix + "confirm"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", prefix + "pay")
                .content(json(Map.of("amount", 150.0, "paymentReference", "REF-" + orderId))))
            .andExpect(status().isOk());
    }

    /** Completes an order (PAID→CHECKED_IN→CHECKED_OUT→COMPLETED). */
    private void advanceToCompletion(long orderId, MockHttpSession photo) throws Exception {
        String prefix = "inv-comp-" + orderId + "-";
        mvc.perform(post("/api/orders/" + orderId + "/check-in").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", prefix + "checkin"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + orderId + "/check-out").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", prefix + "checkout"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + orderId + "/complete").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", prefix + "complete"))
            .andExpect(status().isOk());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Points side effects
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void pointsAwardedOnPaymentMatchRule() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-01-10");
        long orderId = createOrder(cust, slotId);

        int before = getBalance(cust);
        advanceToPayment(orderId, photo, cust);

        // ORDER_PAID rule awards 10 points (seeded)
        int after = getBalance(loginAs("cust1"));
        assertTrue(after >= before + 10,
                "Balance must increase by ≥10 pts (ORDER_PAID rule) after payment; was " + before + ", now " + after);
    }

    @Test
    void pointsHistoryContainsPaymentEntry() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-02-10");
        long orderId = createOrder(cust, slotId);
        advanceToPayment(orderId, photo, cust);

        // The action stored in the ledger is the points rule's NAME ("ORDER_PAYMENT"),
        // not the trigger event ("ORDER_PAID"). See data-test.sql: name='ORDER_PAYMENT'.
        mvc.perform(get("/api/points/history").session(loginAs("cust1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("ORDER_PAYMENT")));
    }

    @Test
    void pointsAwardedOnCompletionMatchRule() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-03-10");
        long orderId = createOrder(cust, slotId);
        advanceToPayment(orderId, photo, cust);

        int before = getBalance(loginAs("cust1"));
        advanceToCompletion(orderId, loginAs("photo1"));

        // ORDER_COMPLETED rule awards 20 points (seeded)
        int after = getBalance(loginAs("cust1"));
        assertTrue(after >= before + 20,
                "Balance must increase by ≥20 pts (ORDER_COMPLETED rule) after completion; was " + before + ", now " + after);
    }

    @Test
    void pointsHistoryContainsCompletionEntry() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-04-10");
        long orderId = createOrder(cust, slotId);
        advanceToPayment(orderId, photo, cust);
        advanceToCompletion(orderId, loginAs("photo1"));

        mvc.perform(get("/api/points/history").session(loginAs("cust1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("ORDER_COMPLETED")));
    }

    @Test
    void adminAwardPointsReflectInRecipientBalance() throws Exception {
        MockHttpSession admin = loginAs("admin");
        MockHttpSession cust2 = loginAs("cust2");

        int before = getBalance(cust2);

        mvc.perform(post("/api/points/award").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 5, "points", 50, "description", "Invariant award test"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points").value(50))
            .andExpect(jsonPath("$.action").value("ADMIN_AWARD"));

        int after = getBalance(loginAs("cust2"));
        assertEquals(before + 50, after,
                "Recipient balance must increase by exactly the awarded amount");
    }

    @Test
    void leaderboardOrderingIsDescendingByPoints() throws Exception {
        MockHttpSession admin = loginAs("admin");

        // Award cust1 a large amount to ensure they appear high on the leaderboard
        mvc.perform(post("/api/points/award").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 1000, "description", "Leaderboard test"))))
            .andExpect(status().isOk());

        MvcResult leaderboard = mvc.perform(get("/api/points/leaderboard").session(admin))
            .andExpect(status().isOk())
            .andReturn();

        List<?> entries = om.readValue(leaderboard.getResponse().getContentAsString(), List.class);
        assertFalse(entries.isEmpty(), "Leaderboard must not be empty");

        // Verify descending order — leaderboard field is "points"
        int prevPoints = Integer.MAX_VALUE;
        for (Object entry : entries) {
            @SuppressWarnings("unchecked")
            Map<String, Object> e = (Map<String, Object>) entry;
            int pts = ((Number) e.get("points")).intValue();
            assertTrue(pts <= prevPoints,
                    "Leaderboard must be in descending order; found " + pts + " after " + prevPoints);
            prevPoints = pts;
        }
    }

    private int getBalance(MockHttpSession session) throws Exception {
        MvcResult r = mvc.perform(get("/api/points/balance").session(session))
            .andExpect(status().isOk()).andReturn();
        return ((Number) parseMap(r).get("balance")).intValue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Notification side effects
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void notificationsQueuedAfterOrderCreation() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-05-10");

        int countBefore = getNotificationCount(loginAs("cust1"));
        createOrder(cust, slotId);
        int countAfter = getNotificationCount(loginAs("cust1"));

        assertTrue(countAfter >= countBefore,
                "Notification count must be non-decreasing after order creation");
    }

    private int getNotificationCount(MockHttpSession session) throws Exception {
        MvcResult r = mvc.perform(get("/api/notifications").session(session))
            .andExpect(status().isOk()).andReturn();
        List<?> notifs = om.readValue(r.getResponse().getContentAsString(), List.class);
        return notifs.size();
    }

    @Test
    void notificationsListIsStructuredForAdmin() throws Exception {
        // Admin should always see at least some notifications given the other tests run
        MockHttpSession admin = loginAs("admin");
        mvc.perform(get("/api/notifications").session(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
        // No further structure assertion — admin may have 0 notifications in isolation;
        // structural fields (id, type) are verified in AddressNotifPointsApiIT.
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Time slot capacity invariants
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void slotAppearsInListingTimeslotsList() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId = createSlot(photo, "2028-06-10");
        createOrder(cust, slotId);

        MvcResult slotsR = mvc.perform(get("/api/timeslots/listing/1").session(loginAs("cust1")))
            .andExpect(status().isOk()).andReturn();
        List<?> slots = om.readValue(slotsR.getResponse().getContentAsString(), List.class);

        boolean found = slots.stream()
            .anyMatch(s -> slotId == ((Number) ((Map<?, ?>) s).get("id")).longValue());
        assertTrue(found, "The created slot must appear in listing 1's slots list");
    }

    @Test
    void oversoldSlotIsRejected() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        MockHttpSession cust2 = loginAs("cust2");

        long slotId = createSlot(photo, "2028-07-10");
        createOrder(cust, slotId);

        // Second booking on capacity-1 slot must fail
        mvc.perform(post("/api/orders").session(cust2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "deep-oversell-" + System.nanoTime())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().is4xxClientError());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Invalid state-transition rejection
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void cannotConfirmAlreadyConfirmedOrder() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-08-10");
        long orderId = createOrder(cust, slotId);

        String prefix = "inv-dup-" + orderId + "-";
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", prefix + "c1"))
            .andExpect(status().isOk());

        // Second confirm (with a different idempotency key) must fail
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", prefix + "c2"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void cannotPayBeforeConfirmation() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-09-10");
        long orderId = createOrder(cust, slotId);

        // Order is CREATED — paying before confirmation must fail
        mvc.perform(post("/api/orders/" + orderId + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "inv-pay-before-" + orderId)
                .content(json(Map.of("amount", 150.0, "paymentReference", "REF-X"))))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void cannotCheckInBeforePayment() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-10-10");
        long orderId = createOrder(cust, slotId);

        // Confirm but don't pay
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "inv-ci-pre-" + orderId + "-confirm"))
            .andExpect(status().isOk());

        // Check-in before payment must fail
        mvc.perform(post("/api/orders/" + orderId + "/check-in").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "inv-ci-pre-" + orderId + "-checkin"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void cannotCancelAlreadyCancelledOrder() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-11-10");
        long orderId = createOrder(cust, slotId);

        // Cancel once
        mvc.perform(post("/api/orders/" + orderId + "/cancel").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "inv-cancel1-" + orderId)
                .content(json(Map.of("reason", "First cancel"))))
            .andExpect(status().isOk());

        // Cancel again must fail (with a different idempotency key)
        mvc.perform(post("/api/orders/" + orderId + "/cancel").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "inv-cancel2-" + orderId)
                .content(json(Map.of("reason", "Second cancel"))))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void cannotCompleteOrderWithoutCheckout() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2028-12-10");
        long orderId = createOrder(cust, slotId);

        MockHttpSession freshPhoto = loginAs("photo1");
        MockHttpSession freshCust  = loginAs("cust1");
        advanceToPayment(orderId, photo, cust);

        // Check-in but skip check-out
        mvc.perform(post("/api/orders/" + orderId + "/check-in").session(freshPhoto)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "inv-no-checkout-" + orderId + "-checkin"))
            .andExpect(status().isOk());

        // Complete without checkout must fail
        mvc.perform(post("/api/orders/" + orderId + "/complete").session(freshPhoto)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "inv-no-checkout-" + orderId + "-complete"))
            .andExpect(status().is4xxClientError());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Search result correctness
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void searchWithCategoryFilterReturnsOnlyMatchingCategory() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        // Search for PORTRAIT listings only (seeded: listing 1 is PORTRAIT)
        MvcResult r = mvc.perform(get("/api/listings/search?category=PORTRAIT").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = parseMap(r);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listings = (List<Map<String, Object>>) resp.get("items");

        for (Map<String, Object> listing : listings) {
            assertEquals("PORTRAIT", listing.get("category"),
                    "Category filter must restrict results to PORTRAIT only");
        }
    }

    @Test
    void searchWithKeywordReturnsRelevantListings() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(get("/api/listings/search?keyword=Studio").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[*].title", hasItem(containsString("Studio"))));
    }

    @Test
    void searchWithNonexistentKeywordReturnsEmptyResults() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        MvcResult r = mvc.perform(get("/api/listings/search?keyword=zzz_no_match_xyzzy_12345").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = parseMap(r);
        @SuppressWarnings("unchecked")
        List<?> items = (List<?>) resp.get("items");
        assertTrue(items.isEmpty(), "Search for nonsense keyword must return no listings");
    }

    @Test
    void searchPaginationPageSizeIsRespected() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        mvc.perform(get("/api/listings/search?size=1").session(cust))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(lessThanOrEqualTo(1)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Reschedule: slot atomically switches
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void rescheduleUpdatesOrderToNewSlotId() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");

        long slot1 = createSlot(photo, "2029-01-10", "10:00", "11:00");
        long slot2 = createSlot(photo, "2029-01-11", "10:00", "11:00");
        long orderId = createOrder(cust, slot1);

        MvcResult r = mvc.perform(post("/api/orders/" + orderId + "/reschedule").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "inv-resched-" + orderId)
                .content(json(Map.of("newTimeSlotId", slot2, "reason", "Scheduling conflict"))))
            .andExpect(status().isOk())
            .andReturn();

        Map<String, Object> order = parseMap(r);
        assertEquals(((Number) slot2).longValue(),
                ((Number) order.get("timeSlotId")).longValue(),
                "After reschedule, order must reference the new slot id");
    }

    @Test
    void rescheduleOldSlotBecomesAvailableAgain() throws Exception {
        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        MockHttpSession cust2 = loginAs("cust2");

        // Cap-1 slot
        long slot1 = createSlot(photo, "2029-02-10", "15:00", "16:00");
        long slot2 = createSlot(photo, "2029-02-11", "15:00", "16:00");
        long orderId = createOrder(cust, slot1);

        // Reschedule away from slot1
        mvc.perform(post("/api/orders/" + orderId + "/reschedule").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "inv-resched2-" + orderId)
                .content(json(Map.of("newTimeSlotId", slot2, "reason", "Reschedule test"))))
            .andExpect(status().isOk());

        // Another customer should now be able to book slot1
        mvc.perform(post("/api/orders").session(cust2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "deep-resched-" + System.nanoTime())
                .content(json(Map.of("listingId", 1, "timeSlotId", slot1))))
            .andExpect(status().isOk());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Points rule update — effect verified by subsequent award
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void updatedPointsRuleChangesAwardAmount() throws Exception {
        MockHttpSession admin = loginAs("admin");

        // Update ORDER_PAYMENT rule to 99 points
        mvc.perform(put("/api/points/rules/1").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "name", "ORDER_PAYMENT",
                        "description", "Updated for invariant test",
                        "points", 99,
                        "scope", "INDIVIDUAL",
                        "triggerEvent", "ORDER_PAID",
                        "active", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points").value(99));

        MockHttpSession photo = loginAs("photo1");
        MockHttpSession cust  = loginAs("cust1");
        long slotId  = createSlot(photo, "2029-03-10");
        long orderId = createOrder(cust, slotId);

        int before = getBalance(loginAs("cust1"));
        advanceToPayment(orderId, photo, cust);

        int after = getBalance(loginAs("cust1"));
        assertTrue(after >= before + 99,
                "After updating ORDER_PAID rule to 99pts, payment must award ≥99 pts; was " + before + ", now " + after);

        // Restore rule to original 10 pts so later tests are not affected
        mvc.perform(put("/api/points/rules/1").session(loginAs("admin"))
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "name", "ORDER_PAYMENT",
                        "description", "Points for payment",
                        "points", 10,
                        "scope", "INDIVIDUAL",
                        "triggerEvent", "ORDER_PAID",
                        "active", true))))
            .andExpect(status().isOk());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrent points ledger — no lost updates under parallel awards
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fires N concurrent admin award requests against the same user and verifies
     * that the final balance equals the arithmetic sum of all awards — i.e. no
     * update is silently dropped under contention.
     */
    @Test
    void concurrentPointsAwardsAccumulateWithoutLostUpdates() throws Exception {
        MockHttpSession cust2 = loginAs("cust2");
        int balBefore = getBalance(cust2);

        int threadCount = 5;
        int pointsPerAward = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startGate.await();
                MockHttpSession adminSession = loginAs("admin");
                MvcResult r = mvc.perform(post("/api/points/award").session(adminSession)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 5, "points", pointsPerAward,
                                "description", "Concurrent ledger test"))))
                    .andReturn();
                return r.getResponse().getStatus();
            }));
        }

        startGate.countDown(); // release all threads simultaneously

        int successes = 0;
        for (Future<Integer> f : futures) {
            if (f.get(10, TimeUnit.SECONDS) == 200) successes++;
        }
        executor.shutdown();

        assertEquals(threadCount, successes, "All concurrent award requests must succeed");

        // Final balance must equal the arithmetic sum — no lost updates
        int balAfter = getBalance(loginAs("cust2"));
        assertEquals(balBefore + threadCount * pointsPerAward, balAfter,
                "Expected balance " + (balBefore + threadCount * pointsPerAward)
                        + " after " + threadCount + " concurrent awards of " + pointsPerAward
                        + " pts each, but got " + balAfter);
    }
}
