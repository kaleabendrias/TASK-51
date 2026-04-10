package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

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
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 3, "slotDate", "2026-09-10",
                        "startTime", "09:00", "endTime", "11:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int freshSlot = ((Number) parseMap(slotR).get("id")).intValue();

        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-create")
                .content(json(Map.of("listingId", 3, "timeSlotId", freshSlot))))
            .andExpect(status().isOk()).andReturn();
        int id = ((Number) parseMap(r).get("id")).intValue();
        mvc.perform(post("/api/orders/" + id + "/confirm").session(photo)
                .header("Idempotency-Key", "cov-refund-c")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/pay").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-p")
                .content(json(Map.of("amount", 300.0, "paymentReference", "R1")))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/check-in").session(photo)
                .header("Idempotency-Key", "cov-refund-ci")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/check-out").session(photo)
                .header("Idempotency-Key", "cov-refund-co")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/complete").session(photo)
                .header("Idempotency-Key", "cov-refund-done")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/refund").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-refund-refund")
                .content(json(Map.of("amount", 300.0, "reason", "Quality issue"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test void rescheduleOrder() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        MvcResult slot1R = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-08-01",
                        "startTime", "14:00", "endTime", "15:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotA = ((Number) parseMap(slot1R).get("id")).intValue();
        MvcResult slot2R = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2026-08-02",
                        "startTime", "14:00", "endTime", "15:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotB = ((Number) parseMap(slot2R).get("id")).intValue();
        MvcResult r = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-resched-create")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotA))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(r).get("id")).intValue();
        mvc.perform(post("/api/orders/" + orderId + "/reschedule").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "cov-resched-do")
                .content(json(Map.of("newTimeSlotId", slotB))))
            .andExpect(status().isOk());
    }

    @Test void chatImageDownload() throws Exception {
        MockHttpSession cust = loginAs("cust1");
        MockHttpSession photo = loginAs("photo1");
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-07-01",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 1))))
            .andExpect(status().isOk()).andReturn();
        int slotId = ((Number) parseMap(slotR).get("id")).intValue();
        MvcResult orderR = mvc.perform(post("/api/orders").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "chat-dl-order")
                .content(json(Map.of("listingId", 1, "timeSlotId", slotId))))
            .andExpect(status().isOk()).andReturn();
        int orderId = ((Number) parseMap(orderR).get("id")).intValue();

        MvcResult sendR = mvc.perform(post("/api/messages/send").session(cust)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("recipientId", 2, "content", "hi for download", "orderId", orderId))))
            .andExpect(status().isOk()).andReturn();
        long convId = ((Number) parseMap(sendR).get("conversationId")).longValue();

        MockMultipartFile img = new MockMultipartFile("file", "dl.jpg",
                "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF});
        MvcResult imgR = mvc.perform(multipart("/api/messages/conversations/" + convId + "/image")
                .file(img).session(cust))
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
        mvc.perform(delete("/api/addresses/1").session(s))
            .andExpect(status().isForbidden());
    }

    @Test void pointsHistoryForUser() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/points/history").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test void notificationPreferencesUpdate() throws Exception {
        MockHttpSession s = loginAs("photo1");
        mvc.perform(get("/api/notifications/preferences").session(s))
            .andExpect(status().isOk());
        mvc.perform(put("/api/notifications/preferences").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderUpdates", true, "holds", false,
                        "reminders", true, "approvals", true,
                        "compliance", true, "muteNonCritical", false))))
            .andExpect(status().isOk());
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "X", "price", 50, "durationMinutes", 30))))
            .andExpect(status().isForbidden());
    }

    @Test void updateListingByNonOwnerDenied() throws Exception {
        MockHttpSession s = loginAs("photo2");
        mvc.perform(put("/api/listings/1").session(s)
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

    @Test void searchSuggestionsEndpoint() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?keyword=portrait").session(s))
            .andExpect(status().isOk());
        mvc.perform(get("/api/listings/search/suggestions").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test void sseStreamEndpointConnects() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/messages/stream").session(s))
            .andExpect(status().isOk());
    }

    @Test void disabledUserBlockedOnNextRequest() throws Exception {
        MockHttpSession admin = loginAs("admin");
        MockHttpSession cust2 = loginAs("cust2");
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", false))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/orders").session(cust2))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", containsString("disabled")));
        mvc.perform(patch("/api/users/5/enabled").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("enabled", true))))
            .andExpect(status().isOk());
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
