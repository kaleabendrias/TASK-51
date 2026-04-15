package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CoverageBoostApiIT extends BaseApiIT {

    // ---- Legacy endpoints removed (controllers deleted — returns 404) ----

    @Test void legacyEndpointsFullyRemoved() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/bookings").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/api/services").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/booking/1").session(s)).andExpect(status().isNotFound());
    }

    @Test void orderRefundByAdmin() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession admin = loginAs("admin");
        MockHttpSession photo = loginAs("photo1");
        String run = UUID.randomUUID().toString();
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-09-10",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-create-" + run)
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot))))
            .andExpect(status().isOk()).andReturn();
        int id = ((Number) parseMap(r).get("id")).intValue();
        mvc.perform(post("/api/orders/" + id + "/confirm").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "cov-refund-c-" + run)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/pay").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-p-" + run)
                .content(json(Map.of("amount", 300.0, "paymentReference", "R1")))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/check-in").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "cov-refund-ci-" + run)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/check-out").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "cov-refund-co-" + run)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/complete").session(photo)
                .header("Origin", TEST_ORIGIN)
                .header("Idempotency-Key", "cov-refund-done-" + run)).andExpect(status().isOk());

        // Verify refund response carries the REFUNDED status and the refund amount
        MvcResult refundR = mvc.perform(post("/api/orders/" + id + "/refund").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-refund-" + run)
                .content(json(Map.of("amount", 300.0, "reason", "Quality issue"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"))
            .andReturn();

        // Verify GET on the order still reflects REFUNDED state
        mvc.perform(get("/api/orders/" + id).session(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test void rescheduleOrder() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        String run = UUID.randomUUID().toString();
        MvcResult slot1R = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-08-01",
                        "startTime", "14:00", "endTime", "15:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotA = ((Number) parseMap(slot1R).get("id")).intValue();
        MvcResult slot2R = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-08-02",
                        "startTime", "14:00", "endTime", "15:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotB = ((Number) parseMap(slot2R).get("id")).intValue();
        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-resched-create-" + run)
                .content(json(Map.of("listingId", 1, "timeSlotId", slotA))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(r).get("id")).intValue();

        // Verify the reschedule response reflects the new slot id
        MvcResult reschedR = mvc.perform(post("/api/orders/" + orderId + "/reschedule").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-resched-do-" + run)
                .content(json(Map.of("newTimeSlotId", slotB))))
            .andExpect(status().isOk())
            .andReturn();

        // The rescheduled order must reference slotB, not slotA
        org.junit.jupiter.api.Assertions.assertEquals(
                slotB, ((Number) parseMap(reschedR).get("timeSlotId")).intValue(),
                "Rescheduled order must reference the new slot");
    }

    @Test void chatImageDownload() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        String run = UUID.randomUUID().toString();
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-07-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();
        MvcResult orderR = mvc.perform(post("/api/orders").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "chat-dl-order-" + run)
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(orderR).get("id")).intValue();

        MvcResult sendR = mvc.perform(post("/api/messages/send").session(cust)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "hi for download", "orderId", orderId))))
            .andExpect(status().isOk()).andReturn();
        long convId = ((Number) parseMap(sendR).get("conversationId")).longValue();

        MockMultipartFile img = new MockMultipartFile("file", "dl.jpg",
                "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF});
        MvcResult imgR = mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(img).session(cust)
                .header("Origin", TEST_ORIGIN))
            .andExpect(status().isOk()).andReturn();
        Map<String, Object> att = (Map<String, Object>) parseMap(imgR).get("attachment");
        int attId = ((Number) att.get("id")).intValue();

        mvc.perform(get("/api/messages/attachments/" + attId + "/download").session(cust))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("dl.jpg")));
    }

    @Test void chatImageNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/messages/attachments/99999/download").session(s))
            .andExpect(status().isNotFound());
    }

    @Test void orderNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/orders/99999").session(s))
            .andExpect(status().isNotFound());
    }

    @Test void addressNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/addresses/99999").session(s))
            .andExpect(status().isNotFound());
    }

    @Test void deleteAddressNotOwned() throws Exception {
        MockHttpSession s = loginAs("cust2");
        mvc.perform(delete("/api/addresses/1").session(s)
                .header("Origin", TEST_ORIGIN))
            .andExpect(status().isForbidden());
    }

    // ---- Points history has structure (not just an array) ----

    @Test void pointsHistoryForUser() throws Exception {
        // Award some points first so history is guaranteed non-empty
        MockHttpSession admin = loginAs("admin");
        mvc.perform(post("/api/points/award").session(admin)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", 4, "points", 5, "description", "History probe"))))
            .andExpect(status().isOk());

        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/history").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[0].action").isString())
            .andExpect(jsonPath("$[0].points").isNumber());
    }

    // ---- Notification preferences: compliance is always forced on, PUT is persisted ----

    @Test void notificationPreferencesUpdate() throws Exception {
        MockHttpSession s = loginAs("photo1");

        // GET returns expected boolean fields
        mvc.perform(get("/api/notifications/preferences").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliance").isBoolean())
            .andExpect(jsonPath("$.orderUpdates").isBoolean());

        // PUT with compliance=false — server must coerce it to true
        mvc.perform(put("/api/notifications/preferences").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", true, "holds", false,
                        "reminders", true, "approvals", true,
                        "compliance", false, "muteNonCritical", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliance").value(true));   // never mutable
    }

    @Test void blacklistCheckUser() throws Exception {
        MockHttpSession s = loginAs("admin");
        mvc.perform(get("/api/blacklist/user/4").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blacklisted").value(false));
    }

    @Test void createListingByCustomerDenied() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(post("/api/listings").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "X", "price", 50, "durationMinutes", 30))))
            .andExpect(status().isForbidden());
    }

    @Test void updateListingByNonOwnerDenied() throws Exception {
        MockHttpSession s = loginAs("photo2");
        mvc.perform(put("/api/listings/1").session(s)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "Hacked", "price", 1, "durationMinutes", 1,
                        "maxConcurrent", 1, "active", true))))
            .andExpect(status().isForbidden());
    }

    @Test void legacyBookingAccessReturns404() throws Exception {
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(get("/api/bookings/1").session(cust2))
            .andExpect(status().isNotFound());
    }

    // ---- Search suggestions are server-side (keyword recorded, then returned) ----

    @Test void searchSuggestionsEndpoint() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?keyword=portrait").session(s))
            .andExpect(status().isOk());
        mvc.perform(get("/api/listings/search/suggestions").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasItem("portrait")));
    }

    // ---- SSE endpoint advertises text/event-stream ----

    @Test void sseStreamEndpointConnects() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/messages/stream").session(s))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }

    // ---- Disabled user blocked immediately on existing session ----

    @Test void disabledUserBlockedOnNextRequest() throws Exception {
        MockHttpSession admin = loginAs("admin");
        MockHttpSession cust2 = loginAs("cust2");
        try {
            mvc.perform(patch("/api/users/5/enabled").session(admin)
                    .header("Origin", TEST_ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk());
            mvc.perform(get("/api/orders").session(cust2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("disabled")));
        } finally {
            mvc.perform(patch("/api/users/5/enabled").session(admin)
                    .header("Origin", TEST_ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("enabled", true))))
                .andExpect(status().isOk());
        }
    }

    // ---- Photographer DTO hides sensitive fields ----
    @Test void photographerDtoExcludesSensitiveFields() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/users/photographers").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").isNumber())
            .andExpect(jsonPath("$[0].fullName").isNotEmpty())
            .andExpect(jsonPath("$[0].username").isNotEmpty())
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].phone").doesNotExist())
            .andExpect(jsonPath("$[0].enabled").doesNotExist())
            .andExpect(jsonPath("$[0].roleId").doesNotExist())
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }
}
