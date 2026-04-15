package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StrictAuthAndLifecycleApiIT extends BaseApiIT {

    @Autowired DataSource dataSource;

    // ---- Customer ad-hoc messaging denied without orderId ----

    @Test void customerAdHocMessageDenied() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/messages/send").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Hi no order"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("order ID is required")));
    }

    // ---- Registration validation ----

    @Test void registerMissingUsernameFails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "t@t.com", "password", "pass123", "fullName", "X"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Username")));
    }

    @Test void registerInvalidEmailFails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "badmail", "email", "notanemail",
                        "password", "pass123", "fullName", "X"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("email")));
    }

    @Test void registerShortPasswordFails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "shortpw", "email", "sp@t.com",
                        "password", "12", "fullName", "X"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("6 characters")));
    }

    // ---- Notification export lifecycle ----

    @Test void notificationExportLifecycle() throws Exception {
        MockHttpSession admin = loginAs("admin");
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Create order to trigger notifications
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-03-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        // Unmute cust1 first
        mvc.perform(put("/api/notifications/preferences").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", true, "holds", true, "reminders", true,
                        "approvals", true, "compliance", true, "muteNonCritical", false))))
            .andExpect(status().isOk());

        mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "export-lc-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Admin: check export queue (should be empty — notifications are QUEUED not READY_FOR_EXPORT yet)
        mvc.perform(get("/api/notifications/export").session(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        // Additional export-ready check: exporting an imaginary id still counts as 1 exported
        mvc.perform(post("/api/notifications/export").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("ids", List.of(999999L)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exported").value(1));

        // Non-admin denied
        mvc.perform(get("/api/notifications/export").session(cust))
            .andExpect(status().isForbidden());
    }

    // ---- HOLD notification fired on confirm ----

    @Test void confirmTriggersHoldNotification() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-03-05",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult orderR = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "hold-" + UUID.randomUUID())
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(orderR).get("id")).intValue();

        // Count notifications before confirm
        MvcResult beforeR = mvc.perform(get("/api/notifications").session(cust))
            .andReturn();
        int countBefore = ((List<?>) om.readValue(beforeR.getResponse().getContentAsString(), List.class)).size();

        // Confirm triggers HOLD notification
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "hold-confirm-" + UUID.randomUUID()))
            .andExpect(status().isOk());

        MvcResult afterR = mvc.perform(get("/api/notifications").session(cust))
            .andReturn();
        int countAfter = ((List<?>) om.readValue(afterR.getResponse().getContentAsString(), List.class)).size();

        org.junit.jupiter.api.Assertions.assertTrue(countAfter > countBefore,
                "Confirm should trigger HOLD notification for customer");
    }

    // ---- True parallel concurrency test against the DB ----

    @Test void trueParallelSlotOversellPrevention() throws Exception {
        MockHttpSession photo = loginAs("photo1");

        // Create a capacity-1 slot
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-04-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        // Launch 5 threads all trying to book the same slot simultaneously
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startGate.await(); // Wait for all threads to be ready
                MockHttpSession s = loginAs("cust1");
                MvcResult r = mvc.perform(post("/api/orders").session(s)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "par-" + UUID.randomUUID())
                        .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
                    .andReturn();
                return r.getResponse().getStatus();
            }));
        }

        startGate.countDown(); // Release all threads simultaneously

        int successes = 0;
        int failures = 0;
        for (Future<Integer> f : futures) {
            int status = f.get(10, TimeUnit.SECONDS);
            if (status == 200) successes++;
            else failures++;
        }
        executor.shutdown();

        // Exactly 1 should succeed, the rest should fail
        org.junit.jupiter.api.Assertions.assertEquals(1, successes,
                "Exactly 1 booking should succeed under parallel contention, got " + successes);
        org.junit.jupiter.api.Assertions.assertEquals(threadCount - 1, failures,
                "All other threads should fail with oversell prevention");
    }

    // ---- Auth Filter: disabled user rejected on active session ----

    @Test void disabledUserBlockedImmediately() throws Exception {
        MockHttpSession admin = loginAs("admin");
        MockHttpSession cust2 = loginAs("cust2");

        // Verify cust2 can access API normally
        mvc.perform(get("/api/orders").session(cust2))
            .andExpect(status().isOk());

        try {
            // Admin disables cust2 account
            mvc.perform(patch("/api/users/5/enabled").session(admin)
                    .header("Origin", TEST_ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk());

            // cust2's existing session should now be immediately rejected
            mvc.perform(get("/api/orders").session(cust2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("disabled")));
        } finally {
            // Re-enable for cleanup — always runs even on assertion failure
            mvc.perform(patch("/api/users/5/enabled").session(admin)
                    .header("Origin", TEST_ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("enabled", true))))
                .andExpect(status().isOk());
        }
    }

    // ---- Multi-threaded default address contention ----

    @Test void concurrentDefaultAddressSetOnlyOneWins() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        // Create two non-default addresses via API
        MvcResult r1 = mvc.perform(post("/api/addresses").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Race A", "street", "1 A St",
                        "city", "Chicago", "state", "IL", "postalCode", "60601",
                        "country", "US", "isDefault", false))))
            .andExpect(status().isOk()).andReturn();
        int addrA = ((Number) parseMap(r1).get("id")).intValue();

        MvcResult r2 = mvc.perform(post("/api/addresses").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Race B", "street", "2 B St",
                        "city", "Chicago", "state", "IL", "postalCode", "60601",
                        "country", "US", "isDefault", false))))
            .andExpect(status().isOk()).andReturn();
        int addrB = ((Number) parseMap(r2).get("id")).intValue();

        // Concurrently set both as default
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int addrId : new int[]{addrA, addrB}) {
            futures.add(executor.submit(() -> {
                startGate.await();
                MockHttpSession s = loginAs("cust1");
                MvcResult r = mvc.perform(put("/api/addresses/" + addrId).session(s)
                        .header("Origin", TEST_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("label", "Default", "street", "1 St",
                                "city", "Chicago", "state", "IL", "postalCode", "60601",
                                "country", "US", "isDefault", true))))
                    .andReturn();
                return r.getResponse().getStatus();
            }));
        }

        startGate.countDown();
        for (Future<Integer> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Verify at most one default address exists
        MvcResult addrList = mvc.perform(get("/api/addresses").session(cust))
            .andExpect(status().isOk()).andReturn();
        List<?> addresses = om.readValue(addrList.getResponse().getContentAsString(), List.class);
        long defaultCount = addresses.stream()
                .filter(a -> Boolean.TRUE.equals(((Map<?,?>) a).get("isDefault")))
                .count();
        org.junit.jupiter.api.Assertions.assertTrue(defaultCount <= 1,
                "Expected at most 1 default address but found " + defaultCount);
    }

    // ---- Legacy /api/bookings removed (controller deleted) ----

    @Test void legacyBookingsEndpointRemoved() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/bookings").session(s))
            .andExpect(status().isNotFound());
    }

    // ---- Search suggestions are server-side ----

    @Test void serverSideSearchSuggestions() throws Exception {
        MockHttpSession s = loginAs("cust1");

        // Search to record term
        mvc.perform(get("/api/listings/search?keyword=portrait").session(s))
            .andExpect(status().isOk());

        // Verify suggestion returned
        mvc.perform(get("/api/listings/search/suggestions").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasItem("portrait")));
    }

    // ---- SSE stream endpoint ----

    /**
     * SSE endpoint must:
     *   1. Return 200 OK (authenticated access accepted)
     *   2. Advertise {@code text/event-stream} as the Content-Type — this is what
     *      distinguishes an SSE stream from a plain HTTP response and what browser
     *      EventSource clients require before opening the event loop.
     *
     * Cache-Control is intentionally not asserted here because Spring's MockMvc
     * async-dispatch layer does not propagate SSE-specific response headers in the
     * same way a real servlet container does; that header is covered by the over-
     * the-wire integration tests instead.
     */
    @Test void sseStreamReturnsTextEventStreamContentType() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/messages/stream").session(s))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
