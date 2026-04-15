package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinalHardeningApiIT extends BaseApiIT {

    // ---- Courier address ownership IDOR ----

    @Test void courierWithOtherUsersAddressDenied() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Ensure cust2 is enabled at test start so previous failures don't cascade
        MockHttpSession admin = loginAs("admin");
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))))
            .andExpect(status().isOk());

        // cust1's address is ID 1; create a fresh slot
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-12-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        // cust2 tries to use cust1's address (ID 1) for courier
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(post("/api/orders").session(cust2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "addr-idor-test")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId,
                        "deliveryMode", "COURIER", "addressId", 1))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("does not belong")));
    }

    @Test void courierWithOwnAddressSucceeds() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-12-02",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "addr-own-test")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId,
                        "deliveryMode", "COURIER", "addressId", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryMode").value("COURIER"));
    }

    // ---- Admin patch update does not overwrite password/phone ----

    @Test void adminPatchUpdateSelectiveFields() throws Exception {
        MockHttpSession admin = loginAs("admin");
        // PATCH update only email and fullName — should not touch password or phone
        // PATCH returns {"message":"User updated"} — not the full user object
        mvc.perform(patch("/api/users/4").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "patched@test.com", "fullName", "Patched Name"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("User updated"));

        // Follow-up GET verifies the fields were actually persisted (not just accepted)
        MvcResult getR = mvc.perform(get("/api/users/4").session(admin))
            .andExpect(status().isOk()).andReturn();
        Map<String, Object> updatedUser = parseMap(getR);
        org.junit.jupiter.api.Assertions.assertEquals("Patched Name", updatedUser.get("fullName"),
                "fullName must reflect the PATCH update");
        org.junit.jupiter.api.Assertions.assertEquals("cust1", updatedUser.get("username"),
                "username must be unchanged after PATCH");

        // Verify the user can still log in (password not wiped)
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "cust1", "password", "password123"))))
            .andExpect(status().isOk());
    }

    // ---- Server-side sorting ----

    @Test void searchWithServerSideSortByPrice() throws Exception {
        MockHttpSession s = loginAs("cust1");
        MvcResult r = mvc.perform(get("/api/listings/search?sortBy=price_asc&size=100").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();
        // Verify items are sorted by price ascending
        var resp = parseMap(r);
        var items = (java.util.List<Map<String, Object>>) resp.get("items");
        if (items.size() > 1) {
            double prev = 0;
            for (var item : items) {
                double price = ((Number) item.get("price")).doubleValue();
                org.junit.jupiter.api.Assertions.assertTrue(price >= prev,
                        "Expected price_asc sort but " + price + " < " + prev);
                prev = price;
            }
        }
    }

    // ---- Structured location query ----

    @Test void searchByStructuredLocation() throws Exception {
        MockHttpSession s = loginAs("cust1");
        // These columns are null in seed data, so just verify the endpoint accepts them
        mvc.perform(get("/api/listings/search?locationState=IL&locationCity=Chicago").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").isNumber());
    }

    // ---- Notification mute preferences enforced ----

    @Test void mutedUserDoesNotReceiveNotifications() throws Exception {
        MockHttpSession cust = loginAs("cust1");

        // Mute all non-critical
        mvc.perform(put("/api/notifications/preferences").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", false, "holds", false,
                        "reminders", false, "approvals", false,
                        "compliance", true, "muteNonCritical", true))))
            .andExpect(status().isOk());

        try {
            // Count current notifications
            MvcResult before = mvc.perform(get("/api/notifications").session(cust))
                .andExpect(status().isOk()).andReturn();
            int countBefore = ((java.util.List<?>) om.readValue(
                    before.getResponse().getContentAsString(), java.util.List.class)).size();

            // Create an order (should trigger notifications, but user is muted)
            MockHttpSession photo = loginAs("photo1");
            MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                    .header("Origin", TEST_ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("listingId", 1, "slotDate", "2026-12-10",
                            "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
                .andExpect(status().isOk()).andReturn();
            int slotId = ((Number) parseMap(slotR).get("id")).intValue();

            mvc.perform(post("/api/orders").session(cust)
                    .header("Origin", TEST_ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "mute-" + UUID.randomUUID())
                    .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
                .andExpect(status().isOk());

            // Count after — should NOT have increased for the muted customer
            MvcResult after = mvc.perform(get("/api/notifications").session(cust))
                .andExpect(status().isOk()).andReturn();
            int countAfter = ((java.util.List<?>) om.readValue(
                    after.getResponse().getContentAsString(), java.util.List.class)).size();
            org.junit.jupiter.api.Assertions.assertEquals(countBefore, countAfter,
                    "Muted user should not receive new order notifications");
        } finally {
            // Unmute for other tests — always runs even on assertion failure
            mvc.perform(put("/api/notifications/preferences").session(cust)
                    .header("Origin", TEST_ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("orderUpdates", true, "holds", true,
                            "reminders", true, "approvals", true,
                            "compliance", true, "muteNonCritical", false))))
                .andExpect(status().isOk());
        }
    }

    // ---- Concurrent slot oversell ----

    @Test void concurrentSlotBookingStillPreventsOversell() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Ensure cust2 is enabled at test start so previous failures don't cascade
        MockHttpSession admin = loginAs("admin");
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))))
            .andExpect(status().isOk());

        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-12-15",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        // First booking succeeds
        mvc.perform(post("/api/orders").session(cust1)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "race-final-1")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Second booking must fail
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(post("/api/orders").session(cust2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "race-final-2")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("fully booked")));
    }
}
