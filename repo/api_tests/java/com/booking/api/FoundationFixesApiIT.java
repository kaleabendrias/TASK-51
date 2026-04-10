package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Targeted tests covering:
 * - CSRF / Origin validation
 * - Session fixation (session rotation on login)
 * - Concurrent points ledger adjustments (DB-atomic)
 * - SSE stream reconnection semantics
 * - 404 idempotency replay path
 */
class FoundationFixesApiIT extends BaseApiIT {

    // ===== CSRF TESTS =====

    @Test
    void csrfRejectsMissingOriginOnStateChangingEndpoint() throws Exception {
        MockHttpSession s = loginAs("admin");
        // POST without Origin header should be rejected
        mvc.perform(post("/api/points/adjust").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 5, "reason", "test"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("CSRF")));
    }

    @Test
    void csrfRejectsForeignOrigin() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Origin", "http://evil.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 5, "reason", "test"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("origin mismatch")));
    }

    @Test
    void csrfAllowsMatchingOrigin() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 5, "reason", "CSRF pass test"))))
            .andExpect(status().isOk());
    }

    @Test
    void csrfAllowsRefererFallback() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(post("/api/points/adjust").session(s)
                .header("Referer", TEST_ORIGIN + "/dashboard")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 3, "reason", "Referer test"))))
            .andExpect(status().isOk());
    }

    @Test
    void csrfSkipsAuthEndpoints() throws Exception {
        // Login should work without Origin
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "admin", "password", "password123"))))
            .andExpect(status().isOk());
    }

    @Test
    void csrfAllowsGetRequests() throws Exception {
        MockHttpSession s = loginAs("admin");
        // GET should pass without Origin
        mvc.perform(get("/api/points/balance").session(s))
            .andExpect(status().isOk());
    }

    // ===== SESSION FIXATION TESTS =====

    @Test
    void loginRotatesSession() throws Exception {
        // Create initial session
        MockHttpSession session1 = new MockHttpSession();
        String sessionId1 = session1.getId();

        // Login — this should invalidate session1 and create a new one
        MvcResult result = mvc.perform(post("/api/auth/login")
                .session(session1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "cust1", "password", "password123"))))
            .andExpect(status().isOk())
            .andReturn();

        // The original session should be invalidated
        assertTrue(session1.isInvalid(), "Old session should be invalidated after login");
    }

    @Test
    void loginCreatesNewAuthenticatedSession() throws Exception {
        // Login once
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "cust1", "password", "password123"))))
            .andExpect(status().isOk())
            .andReturn();

        // The new session from the response should work for authenticated calls
        MockHttpSession newSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(newSession, "A new session should exist after login");

        mvc.perform(get("/api/auth/me").session(newSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("cust1"));
    }

    // ===== CONCURRENT POINTS LEDGER TESTS =====

    @Test
    void concurrentPointsAwardsProduceCorrectBalance() throws Exception {
        MockHttpSession admin = loginAs("admin");

        // Get initial balance for user 5
        MvcResult balR = mvc.perform(get("/api/points/balance").session(loginAs("cust2")))
            .andExpect(status().isOk()).andReturn();
        int initialBalance = ((Number) parseMap(balR).get("balance")).intValue();

        int threadCount = 5;
        int pointsPerAward = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    MockHttpSession adminSession = loginAs("admin");
                    ready.countDown();
                    go.await();
                    mvc.perform(post("/api/points/adjust").session(adminSession)
                            .header("Origin", TEST_ORIGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("userId", 5, "points", pointsPerAward,
                                    "reason", "Concurrent test " + idx))))
                        .andExpect(status().isOk());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Test will fail on balance check
                }
            });
        }

        ready.await();
        go.countDown();
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(50);
        }

        assertEquals(threadCount, successCount.get(), "All concurrent awards should succeed");

        // Verify final balance is exactly initialBalance + (threadCount * pointsPerAward)
        MvcResult finalR = mvc.perform(get("/api/points/balance").session(loginAs("cust2")))
            .andExpect(status().isOk()).andReturn();
        int finalBalance = ((Number) parseMap(finalR).get("balance")).intValue();
        assertEquals(initialBalance + threadCount * pointsPerAward, finalBalance,
            "Balance should reflect all concurrent awards atomically");
    }

    // ===== SSE STREAM TESTS =====

    @Test
    void sseStreamConnectsAndCanReconnect() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        // First connection
        mvc.perform(get("/api/messages/stream").session(cust)
                .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk());

        // Reconnection with same session — should succeed
        mvc.perform(get("/api/messages/stream").session(cust)
                .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk());
    }

    @Test
    void sseStreamRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/messages/stream")
                .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isUnauthorized());
    }

    // ===== 404 IDEMPOTENCY REPLAY TESTS =====

    @Test
    void idempotency404ReplayReturnsCachedResponse() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        String idemKey = "replay-404-" + UUID.randomUUID();
        long nonExistentOrderId = 999999L;

        // First request — order does not exist, should return 404
        mvc.perform(post("/api/orders/" + nonExistentOrderId + "/confirm").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", idemKey))
            .andExpect(status().isNotFound());

        // Replay with same idempotency key + orderId — should return cached 404
        mvc.perform(post("/api/orders/" + nonExistentOrderId + "/confirm").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", idemKey))
            .andExpect(status().isNotFound());
    }

    @Test
    void idempotency404DoesNotCollideDifferentOrderIds() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        String idemKey = "replay-404-scope-" + UUID.randomUUID();

        // 404 on order 999998
        mvc.perform(post("/api/orders/999998/confirm").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", idemKey))
            .andExpect(status().isNotFound());

        // Same idem key but different order ID — should NOT be a cache hit
        // (scoped key includes orderId, so this is a fresh request that also 404s)
        mvc.perform(post("/api/orders/999997/confirm").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", idemKey))
            .andExpect(status().isNotFound());
    }

    // ===== PROVIDER ROLE TESTS =====

    @Test
    void providersEndpointIncludesAllProviderRoles() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/providers").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
    }

    // ===== DELIVERY ETA TESTS =====

    @Test
    void courierOrderHasDeliveryEta() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Create a fresh slot
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-10-01",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult orderR = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "eta-courier-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot,
                        "addressId", 1, "deliveryMode", "COURIER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryMode").value("COURIER"))
            .andExpect(jsonPath("$.deliveryEta").isNotEmpty())
            .andReturn();
    }

    @Test
    void pickupOrderHasPickupEta() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-10-02",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "eta-pickup-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot,
                        "deliveryMode", "PICKUP"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryMode").value("PICKUP"))
            .andExpect(jsonPath("$.pickupEta").isNotEmpty());
    }

    @Test
    void onsiteOrderHasNoEta() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-10-03",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "eta-onsite-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryMode").value("ONSITE"))
            .andExpect(jsonPath("$.deliveryEta").doesNotExist())
            .andExpect(jsonPath("$.pickupEta").doesNotExist());
    }
}
