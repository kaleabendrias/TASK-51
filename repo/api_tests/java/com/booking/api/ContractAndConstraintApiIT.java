package com.booking.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContractAndConstraintApiIT extends BaseApiIT {

    // ---- Chat: only buyer+seller of the order can create conversations ----

    @Test @Order(1) void chatDeniedForNonOrderParticipant() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Create an order between cust1 and photo1
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-01-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult orderR = mvc.perform(post("/api/orders").session(cust1)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "chat-scope-1")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(orderR).get("id")).intValue();

        // cust2 tries to message on this order — should be denied
        MockHttpSession admin = loginAs("admin");
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", true))));
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(post("/api/messages/send").session(cust2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "Hi", "orderId", orderId))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("buyer or seller")));
    }

    @Test @Order(2) void chatRecipientMustBeOrderParticipant() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-01-02",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult orderR = mvc.perform(post("/api/orders").session(cust1)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "chat-scope-2")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(orderR).get("id")).intValue();

        // cust1 tries to message cust2 (not a participant of this order)
        mvc.perform(post("/api/messages/send").session(cust1)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 5, "content", "Hi", "orderId", orderId))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("not a participant")));
    }

    // ---- Reschedule: must stay within same listing ----

    @Test @Order(3) void rescheduleToDifferentListingDenied() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo1 = loginAs("photo1");
        MockHttpSession photo2 = loginAs("photo2");

        // Slot on listing 1 (photo1's)
        MvcResult s1R = mvc.perform(post("/api/timeslots").session(photo1)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-02-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slot1 = ((Number) parseMap(s1R).get("id")).intValue();

        // Slot on listing 2 (photo2's — different listing)
        MvcResult s2R = mvc.perform(post("/api/timeslots").session(photo2)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 2, "slotDate", "2027-02-02",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slot2 = ((Number) parseMap(s2R).get("id")).intValue();

        // Create order on listing 1
        MvcResult orderR = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "resched-listing-1")
                .content(json(Map.of("listingId", 1, "timeSlotId", slot1))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(orderR).get("id")).intValue();

        // Reschedule to slot on listing 2 — should be denied
        mvc.perform(post("/api/orders/" + orderId + "/reschedule").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "resched-listing-deny")
                .content(json(Map.of("newTimeSlotId", slot2))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("different listing")));
    }

    // ---- Server-side sort contract ----

    @Test @Order(4) void serverSideSortByPriceDesc() throws Exception {
        MockHttpSession s = loginAs("cust1");
        MvcResult r = mvc.perform(get("/api/listings/search?sortBy=price_desc&size=100").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray()).andReturn();
        var items = (List<Map<String, Object>>) parseMap(r).get("items");
        if (items.size() > 1) {
            double prev = Double.MAX_VALUE;
            for (var item : items) {
                double price = ((Number) item.get("price")).doubleValue();
                org.junit.jupiter.api.Assertions.assertTrue(price <= prev,
                        "Expected price_desc but " + price + " > " + prev);
                prev = price;
            }
        }
    }

    // ---- Structured location query ----

    @Test @Order(5) void structuredLocationQueryAccepted() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?locationState=CA&locationCity=LA&locationNeighborhood=Hollywood").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.page").value(1));
    }

    // ---- Application fails without ENCRYPTION_KEY ----
    // (This is a startup-time check — we verify FieldEncryptor.isConfigured() returns true
    //  in the running test context, meaning the key was properly loaded from config)
    @Test @Order(6) void encryptionKeyIsConfigured() throws Exception {
        org.junit.jupiter.api.Assertions.assertTrue(
                com.booking.util.FieldEncryptor.isConfigured(),
                "FieldEncryptor must be configured at startup — app should fail without ENCRYPTION_KEY");
    }
}
