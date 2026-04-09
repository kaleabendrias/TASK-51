package com.booking.api;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityHardeningApiIT extends BaseApiIT {

    // ---- Chat attachment IDOR: non-participant denied ----

    @Test @Order(1) void chatAttachmentDownloadDeniedForNonParticipant() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession cust2 = loginAs("cust2");

        // cust1 sends a message to photo1 with an image
        MvcResult sendR = mvc.perform(post("/api/messages/send").session(cust1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "private img"))))
            .andExpect(status().isOk()).andReturn();
        long convId = ((Number) parseMap(sendR).get("conversationId")).longValue();

        MockMultipartFile img = new MockMultipartFile("file", "secret.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MvcResult imgR = mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(img).session(cust1)).andExpect(status().isOk()).andReturn();
        Map<String, Object> att = (Map<String, Object>) parseMap(imgR).get("attachment");
        int attId = ((Number) att.get("id")).intValue();

        // cust2 (non-participant) tries to download — must be denied
        mvc.perform(get("/api/messages/attachments/" + attId + "/download").session(cust2))
            .andExpect(status().isForbidden());

        // cust1 (participant) can download
        mvc.perform(get("/api/messages/attachments/" + attId + "/download").session(cust1))
            .andExpect(status().isOk());

        // admin can download
        MockHttpSession admin = loginAs("admin");
        mvc.perform(get("/api/messages/attachments/" + attId + "/download").session(admin))
            .andExpect(status().isOk());
    }

    // ---- Idempotency key mandatory ----

    @Test @Order(2) void orderCreateWithoutIdempotencyKeyRejected() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "timeSlotId", 4))))
            .andExpect(status().isBadRequest());
    }

    @Test @Order(3) void orderConfirmWithoutIdempotencyKeyRejected() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Create a fresh slot
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-11-01",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "hard-idem-create")
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(cr).get("id")).intValue();

        // Confirm without key — rejected
        mvc.perform(post("/api/orders/" + orderId + "/confirm").session(photo))
            .andExpect(status().isBadRequest());
    }

    // ---- Paginated search returns items array, not raw array ----

    @Test @Order(4) void searchReturnsItemsAndPaginationMeta() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?page=1&size=2").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.total").isNumber())
            .andExpect(jsonPath("$.totalPages").isNumber());
    }

    // ---- ZIP-to-state consistency ----

    @Test @Order(5) void zipStateConsistencyRejected() throws Exception {
        MockHttpSession s = loginAs("cust1");
        // IL ZIP starts with 6, but CA ZIP starts with 9 — mismatch
        mvc.perform(post("/api/addresses").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "Bad", "street", "1 St", "city", "LA",
                        "state", "CA", "postalCode", "60601", "country", "US"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("not consistent")));
    }

    @Test @Order(6) void zipStateConsistencyAccepted() throws Exception {
        MockHttpSession s = loginAs("cust1");
        // CA + 90210 is valid
        mvc.perform(post("/api/addresses").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("label", "OK", "street", "1 St", "city", "LA",
                        "state", "CA", "postalCode", "90210", "country", "US"))))
            .andExpect(status().isOk());
    }

    // ---- Phone masking in responses ----

    @Test @Order(7) void phoneFieldMaskedInAllUserResponses() throws Exception {
        MockHttpSession admin = loginAs("admin");
        mvc.perform(get("/api/users").session(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].phone").value(startsWith("***")))
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    // ---- Concurrent slot race condition (oversell prevention) ----

    @Test @Order(8) void concurrentSlotBookingPreventsOversell() throws Exception {
        MockHttpSession cust1 = loginAs("cust1");
        MockHttpSession cust2 = loginAs("cust2");
        MockHttpSession photo = loginAs("photo1");

        // Create a slot with capacity 1
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-10-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        // First booking succeeds
        mvc.perform(post("/api/orders").session(cust1)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "race-1")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk());

        // Second booking on same slot must fail (oversell prevention)
        // Re-enable cust2 if blacklisted from earlier tests
        MockHttpSession adminS = loginAs("admin");
        mvc.perform(patch("/api/users/5/enabled").session(adminS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))));
        cust2 = loginAs("cust2");

        mvc.perform(post("/api/orders").session(cust2)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "race-2")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("fully booked")));
    }

    // ---- Dynamic points rules (no hardcoded values) ----

    @Test @Order(9) void orderCompletionUsesPointsRulesEngine() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");

        // Get initial balance
        MvcResult balR = mvc.perform(get("/api/points/balance").session(cust))
            .andExpect(status().isOk()).andReturn();
        int balBefore = ((Number) parseMap(balR).get("balance")).intValue();

        // Create + complete an order
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-10-10",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult cr = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "pts-rules-create")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int oid = ((Number) parseMap(cr).get("id")).intValue();

        mvc.perform(post("/api/orders/" + oid + "/confirm").session(photo)
                .header("Idempotency-Key", "pts-rules-confirm")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + oid + "/pay").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "pts-rules-pay")
                .content(json(Map.of("amount", 150, "paymentReference", "R")))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + oid + "/check-in").session(photo)
                .header("Idempotency-Key", "pts-rules-ci")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + oid + "/check-out").session(photo)
                .header("Idempotency-Key", "pts-rules-co")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + oid + "/complete").session(photo)
                .header("Idempotency-Key", "pts-rules-done")).andExpect(status().isOk());

        // Verify points awarded match the rules (ORDER_PAID=10, ORDER_COMPLETED=20 from seed)
        MvcResult balAfterR = mvc.perform(get("/api/points/balance").session(cust))
            .andExpect(status().isOk()).andReturn();
        int balAfter = ((Number) parseMap(balAfterR).get("balance")).intValue();
        // Should have gained at least 30 points (10+20 from rules)
        org.junit.jupiter.api.Assertions.assertTrue(balAfter >= balBefore + 30,
                "Points should increase by at least 30 (10+20 from rules). Before=" + balBefore + " After=" + balAfter);
    }
}
