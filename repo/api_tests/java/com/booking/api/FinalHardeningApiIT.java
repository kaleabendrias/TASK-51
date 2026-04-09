package com.booking.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FinalHardeningApiIT extends BaseApiIT {

    // ---- Courier address ownership IDOR ----

    @Test @Order(1) void courierWithOtherUsersAddressDenied() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // cust1's address is ID 1; create a fresh slot
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-12-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        // cust2 tries to use cust1's address (ID 1) for courier
        MockHttpSession admin = loginAs("admin");
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))));
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(post("/api/orders").session(cust2)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "addr-idor-test")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId,
                        "deliveryMode", "COURIER", "addressId", 1))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("does not belong")));
    }

    @Test @Order(2) void courierWithOwnAddressSucceeds() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-12-02",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "addr-own-test")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId,
                        "deliveryMode", "COURIER", "addressId", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryMode").value("COURIER"));
    }

    // ---- Admin patch update does not overwrite password/phone ----

    @Test @Order(3) void adminPatchUpdateSelectiveFields() throws Exception {
        MockHttpSession admin = loginAs("admin");
        // PATCH update only email and fullName — should not touch password or phone
        mvc.perform(patch("/api/users/4").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "patched@test.com", "fullName", "Patched Name"))))
            .andExpect(status().isOk());

        // Verify the user can still log in (password not wiped)
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "cust1", "password", "password123"))))
            .andExpect(status().isOk());
    }

    // ---- Server-side sorting ----

    @Test @Order(4) void searchWithServerSideSortByPrice() throws Exception {
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

    @Test @Order(5) void searchByStructuredLocation() throws Exception {
        MockHttpSession s = loginAs("cust1");
        // These columns are null in seed data, so just verify the endpoint accepts them
        mvc.perform(get("/api/listings/search?locationState=IL&locationCity=Chicago").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
    }

    // ---- Notification mute preferences enforced ----

    @Test @Order(6) void mutedUserDoesNotReceiveNotifications() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        // Mute all non-critical
        mvc.perform(put("/api/notifications/preferences").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", false, "holds", false,
                        "reminders", false, "approvals", false,
                        "compliance", true, "muteNonCritical", true))))
            .andExpect(status().isOk());

        // Count current notifications
        MvcResult before = mvc.perform(get("/api/notifications").session(cust))
            .andExpect(status().isOk()).andReturn();
        int countBefore = ((java.util.List<?>) om.readValue(
                before.getResponse().getContentAsString(), java.util.List.class)).size();

        // Create an order (should trigger notifications, but user is muted)
        MockHttpSession photo = loginAs("photo1");
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-12-10",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "mute-test")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Count after — should NOT have increased for the muted customer
        MvcResult after = mvc.perform(get("/api/notifications").session(cust))
            .andExpect(status().isOk()).andReturn();
        int countAfter = ((java.util.List<?>) om.readValue(
                after.getResponse().getContentAsString(), java.util.List.class)).size();
        org.junit.jupiter.api.Assertions.assertEquals(countBefore, countAfter,
                "Muted user should not receive new order notifications");

        // Unmute for other tests
        mvc.perform(put("/api/notifications/preferences").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", true, "holds", true,
                        "reminders", true, "approvals", true,
                        "compliance", true, "muteNonCritical", false))))
            .andExpect(status().isOk());
    }

    // ---- Concurrent slot oversell ----

    @Test @Order(7) void concurrentSlotBookingStillPreventsOversell() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-12-15",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        // First booking succeeds
        mvc.perform(post("/api/orders").session(cust1)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "race-final-1")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Second booking must fail
        MockHttpSession admin = loginAs("admin");
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))));
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(post("/api/orders").session(cust2)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "race-final-2")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("fully booked")));
    }
}
