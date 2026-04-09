package com.booking.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StrictAuthAndLifecycleApiIT extends BaseApiIT {

    @Autowired DataSource dataSource;

    // ---- Customer ad-hoc messaging denied without orderId ----

    @Test @Order(1) void customerAdHocMessageDenied() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Hi no order"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("order ID is required")));
    }

    // ---- Registration validation ----

    @Test @Order(2) void registerMissingUsernameFails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "t@t.com", "password", "pass123", "fullName", "X"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Username")));
    }

    @Test @Order(3) void registerInvalidEmailFails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "badmail", "email", "notanemail",
                        "password", "pass123", "fullName", "X"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("email")));
    }

    @Test @Order(4) void registerShortPasswordFails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "shortpw", "email", "sp@t.com",
                        "password", "12", "fullName", "X"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("6 characters")));
    }

    // ---- Notification export lifecycle ----

    @Test @Order(5) void notificationExportLifecycle() throws Exception {
        MockHttpSession admin = loginAs("admin");
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Create order to trigger notifications
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-03-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        // Unmute cust1 first
        mvc.perform(put("/api/notifications/preferences").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", true, "holds", true, "reminders", true,
                        "approvals", true, "compliance", true, "muteNonCritical", false))))
            .andExpect(status().isOk());

        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "export-lifecycle-1")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Admin: check export queue (should be empty — notifications are QUEUED not READY_FOR_EXPORT yet)
        mvc.perform(get("/api/notifications/export").session(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        // Non-admin denied
        mvc.perform(get("/api/notifications/export").session(cust))
            .andExpect(status().isForbidden());
    }

    // ---- HOLD notification fired on confirm ----

    @Test @Order(6) void confirmTriggersHoldNotification() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-03-05",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult orderR = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "hold-trigger-1")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(orderR).get("id")).intValue();

        // Count notifications before confirm
        MvcResult beforeR = mvc.perform(get("/api/notifications").session(cust))
            .andReturn();
        int countBefore = ((List<?>) om.readValue(beforeR.getResponse().getContentAsString(), List.class)).size();

        // Confirm triggers HOLD notification
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo)
                .header("Idempotency-Key", "hold-trigger-confirm"))
            .andExpect(status().isOk());

        MvcResult afterR = mvc.perform(get("/api/notifications").session(cust))
            .andReturn();
        int countAfter = ((List<?>) om.readValue(afterR.getResponse().getContentAsString(), List.class)).size();

        org.junit.jupiter.api.Assertions.assertTrue(countAfter > countBefore,
                "Confirm should trigger HOLD notification for customer");
    }

    // ---- True parallel concurrency test against the DB ----

    @Test @Order(7) void trueParallelSlotOversellPrevention() throws Exception {
        MockHttpSession photo = loginAs("photo1");

        // Create a capacity-1 slot
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
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
            final int idx = i;
            futures.add(executor.submit(() -> {
                startGate.await(); // Wait for all threads to be ready
                MockHttpSession s = loginAs("cust1");
                MvcResult r = mvc.perform(post("/api/orders").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "race-parallel-" + idx)
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
}
